package org.thetower.tt_luckytower.jackpot;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.thetower.tt_luckytower.LuckyTowerPlugin;
import org.thetower.tt_luckytower.config.FloorConfig;
import org.thetower.tt_luckytower.config.JackpotTowerConfig;
import org.thetower.tt_luckytower.config.TowerConfig;

import java.io.File;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.*;

public class JackpotManager {

    private final LuckyTowerPlugin plugin;

    /**
     * groupId → 현재 잭팟 금액.
     * 같은 그룹의 여러 타워가 이 풀을 공유합니다.
     */
    private final Map<String, Double> jackpotAmounts = new HashMap<>();

    private File dataFile;
    private org.bukkit.configuration.file.YamlConfiguration dataConfig;

    private static final Random RANDOM = new Random();

    public JackpotManager(LuckyTowerPlugin plugin) {
        this.plugin = plugin;
    }

    // ──────────────────── 데이터 로드/저장 ────────────────────

    public void loadData() {
        dataFile = new File(plugin.getDataFolder(), "jackpot_data.yml");
        if (!dataFile.exists()) {
            try { dataFile.createNewFile(); }
            catch (IOException e) { plugin.getLogger().warning("jackpot_data.yml 생성 실패: " + e.getMessage()); }
        }
        dataConfig = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(dataFile);

        jackpotAmounts.clear();
        for (TowerConfig tower : plugin.getTowerManager().getAllTowers()) {
            JackpotTowerConfig jc = tower.getJackpotConfig();
            if (!jc.isEnabled()) continue;
            String gk = tower.getGroupId();
            if (!jackpotAmounts.containsKey(gk)) {
                // 저장된 값 우선, 없으면 초기값
                double saved = dataConfig.getDouble("jackpots." + gk, -1);
                jackpotAmounts.put(gk, saved >= 0 ? saved : jc.getInitialAmount());
            }
        }

        // 홀로그램은 월드 로드 후 (1틱 딜레이)
        plugin.getServer().getScheduler().runTaskLater(plugin, this::refreshAllHolograms, 20L);
        plugin.getLogger().info("잭팟 데이터 로드 완료.");
    }

    private void saveData() {
        for (Map.Entry<String, Double> entry : jackpotAmounts.entrySet()) {
            dataConfig.set("jackpots." + entry.getKey(), entry.getValue());
        }
        try { dataConfig.save(dataFile); }
        catch (IOException e) { plugin.getLogger().warning("잭팟 데이터 저장 실패: " + e.getMessage()); }
    }

    // ──────────────────── 잭팟 풀 관리 ────────────────────

    /** 입장료의 일부를 잭팟 풀에 적립 */
    public void contribute(String towerId, double entryFee) {
        TowerConfig tower = plugin.getTowerManager().getTower(towerId);
        if (tower == null) return;
        JackpotTowerConfig jc = tower.getJackpotConfig();
        if (!jc.isEnabled()) return;

        String gk = tower.getGroupId();
        double contribution = entryFee * jc.getContributionPercent() / 100.0;
        jackpotAmounts.merge(gk, contribution, Double::sum);
        saveData();
        updateHologramsForGroup(gk);
    }

    /** 관리자가 잭팟 금액 직접 설정 */
    public void setAmount(String towerId, double amount) {
        TowerConfig tower = plugin.getTowerManager().getTower(towerId);
        String gk = tower != null ? tower.getGroupId() : towerId;
        jackpotAmounts.put(gk, Math.max(0, amount));
        saveData();
        updateHologramsForGroup(gk);
    }

    public double getAmount(String towerId) {
        TowerConfig tower = plugin.getTowerManager().getTower(towerId);
        if (tower == null) return 0;
        String gk = tower.getGroupId();
        return jackpotAmounts.getOrDefault(gk, tower.getJackpotConfig().getInitialAmount());
    }

    // ──────────────────── 잭팟 판정 ────────────────────

    /**
     * 리셋 층계 도달 시 호출.
     * @return true = 잭팟 당첨
     */
    public boolean rollJackpot(Player player, String towerId, int floorNumber) {
        TowerConfig tower = plugin.getTowerManager().getTower(towerId);
        if (tower == null) return false;
        JackpotTowerConfig jc = tower.getJackpotConfig();
        if (!jc.isEnabled()) return false;

        FloorConfig floor = tower.getFloor(floorNumber);
        if (floor == null || floor.getJackpotChance() <= 0) return false;

        double roll = RANDOM.nextDouble() * 100.0;
        if (roll >= floor.getJackpotChance()) return false;

        // ── 당첨! ──
        String gk = tower.getGroupId();
        double poolTotal = jackpotAmounts.getOrDefault(gk, jc.getInitialAmount());
        double payout = poolTotal * floor.getJackpotPayoutPercent() / 100.0;

        // 금액 차감 (initial-amount 미만으로 내려가지 않음)
        jackpotAmounts.put(gk, Math.max(jc.getInitialAmount(), poolTotal - payout));
        saveData();
        updateHologramsForGroup(gk);

        // 잭팟 금액 지급 (Vault)
        plugin.getVaultHook().deposit(player, payout);

        // 당첨 연출 및 보상
        triggerWinnerRewards(player, tower, payout);

        plugin.getLogger().info("[잭팟] " + player.getName() + " → " + towerId
                + " (그룹: " + gk + ")"
                + " / " + floorNumber + "층 / 지급: " + payout);
        return true;
    }

    /**
     * 마지막 층 클리어 시 100% 확률로 잭팟 지급.
     * last-floor-payout 설정값만큼 풀에서 차감.
     */
    public void triggerLastFloorJackpot(Player player, String towerId) {
        TowerConfig tower = plugin.getTowerManager().getTower(towerId);
        if (tower == null) return;
        JackpotTowerConfig jc = tower.getJackpotConfig();
        if (!jc.isEnabled()) return;

        String gk = tower.getGroupId();
        double poolTotal = jackpotAmounts.getOrDefault(gk, jc.getInitialAmount());
        double payout = poolTotal * jc.getLastFloorJackpotPayout() / 100.0;

        // 금액 차감 (initial-amount 미만으로 내려가지 않음)
        jackpotAmounts.put(gk, Math.max(jc.getInitialAmount(), poolTotal - payout));
        saveData();
        updateHologramsForGroup(gk);

        plugin.getVaultHook().deposit(player, payout);
        triggerWinnerRewards(player, tower, payout);

        plugin.getLogger().info("[잭팟-완주] " + player.getName() + " → " + towerId
                + " / 지급: " + payout + " (" + jc.getLastFloorJackpotPayout() + "%)");
    }

    private void triggerWinnerRewards(Player winner, TowerConfig tower, double payout) {
        JackpotTowerConfig jc = tower.getJackpotConfig();
        String amountStr = NumberFormat.getNumberInstance(Locale.KOREA).format((long) payout) + "원";

        // 구역 내 플레이어 목록
        List<Player> regionPlayers = getPlayersInRegion(tower, winner);

        // ── 아이템 지급 (구역 내 전체) ──
        for (ItemStack item : jc.getWinnerItems()) {
            for (Player p : regionPlayers) {
                HashMap<Integer, ItemStack> leftover = p.getInventory().addItem(item.clone());
                leftover.values().forEach(drop -> p.getWorld().dropItemNaturally(p.getLocation(), drop));
                p.sendMessage("§6[잭팟 보상] §e" + item.getAmount() + "x " + item.getType().name() + " 획득!");
            }
        }

        // ── 당첨자 커맨드 ({player}=당첨자, {amount}=금액, 1회 실행) ──
        for (String cmd : jc.getWinnerCommands()) {
            String parsed = cmd
                    .replace("{player}", winner.getName())
                    .replace("{amount}", amountStr);
            plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), parsed);
        }

        // ── 리전 전원 커맨드 ({player}=각 플레이어, 인당 1회 실행) ──
        if (!jc.getRegionCommands().isEmpty()) {
            for (Player p : regionPlayers) {
                for (String cmd : jc.getRegionCommands()) {
                    String parsed = cmd
                            .replace("{player}", p.getName())
                            .replace("{amount}", amountStr);
                    plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), parsed);
                }
            }
        }

        // ── 당첨자 타이틀 ──
        winner.sendTitle("§6★ JACKPOT! ★", "§e잭팟 §f" + amountStr + " §e획득!", 10, 100, 20);

        // ── 리전 전원 메시지 ({player}=당첨자, {amount}=금액) ──
        List<String> regionMessages = jc.getRegionMessages();
        if (!regionMessages.isEmpty()) {
            for (String msg : regionMessages) {
                String parsed = msg
                        .replace("{player}", winner.getName())
                        .replace("{amount}", amountStr);
                for (Player p : regionPlayers) {
                    p.sendMessage(parsed);
                }
            }
        } else {
            // 기본 메시지 (region-messages 미설정 시)
            for (Player p : regionPlayers) {
                p.sendMessage("§6★ [럭키타워 잭팟] §f" + winner.getName()
                        + "§e님이 §f" + amountStr + " §e잭팟을 터뜨렸습니다!");
            }
        }

        // ── 폭죽 사운드 3번 + 폭죽 엔티티 스폰 ──
        spawnJackpotFireworks(winner, regionPlayers);
    }

    /**
     * 잭팟 폭죽 연출: 사운드 × 3회 (0.75초 간격) + 폭죽 엔티티 스폰
     */
    private void spawnJackpotFireworks(Player winner, List<Player> regionPlayers) {
        Location base = winner.getLocation().clone();

        for (int i = 0; i < 3; i++) {
            final int idx = i;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                // 구역 내 모든 플레이어에게 폭죽 사운드 3종 재생
                for (Player p : regionPlayers) {
                    if (!p.isOnline()) continue;
                    p.playSound(p.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST,       1.0f, 1.0f);
                    p.playSound(p.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 0.9f, 1.0f);
                    p.playSound(p.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_TWINKLE,     0.7f, 1.2f);
                }
                // 당첨자 주변 랜덤 위치에 폭죽 스폰
                Location spawnLoc = base.clone().add(
                        (RANDOM.nextDouble() - 0.5) * 6,
                        RANDOM.nextDouble() * 3 + 1,
                        (RANDOM.nextDouble() - 0.5) * 6
                );
                spawnSingleFirework(spawnLoc, idx);
            }, i * 15L); // 15틱(0.75초) 간격으로 3번
        }
    }

    /**
     * 화려한 폭죽 엔티티 1개 스폰 (즉시 폭발, Power=0)
     */
    private void spawnSingleFirework(Location loc, int variant) {
        if (loc.getWorld() == null) return;
        try {
            Firework fw = loc.getWorld().spawn(loc, Firework.class);
            FireworkMeta meta = fw.getFireworkMeta();

            // 랜덤 색상 팔레트 (황금/금빛 계열 + 보색)
            Color[][] palettes = {
                    {Color.YELLOW, Color.fromRGB(255, 200, 0),   Color.WHITE },
                    {Color.ORANGE, Color.RED,    Color.YELLOW},
                    {Color.WHITE,  Color.LIME,   Color.fromRGB(255, 200, 0)  }
            };
            Color[] chosen = palettes[variant % palettes.length];

            FireworkEffect effect = FireworkEffect.builder()
                    .with(variant == 1 ? FireworkEffect.Type.STAR : FireworkEffect.Type.BALL_LARGE)
                    .withColor(chosen[0], chosen[1])
                    .withFade(chosen[2])
                    .trail(true)
                    .flicker(true)
                    .build();

            meta.addEffect(effect);
            meta.setPower(0); // 즉시 폭발
            fw.setFireworkMeta(meta);
        } catch (Exception e) {
            plugin.getLogger().warning("폭죽 스폰 실패: " + e.getMessage());
        }
    }

    private List<Player> getPlayersInRegion(TowerConfig tower, Player fallback) {
        List<Player> result = new ArrayList<>();
        try {
            RegionManager rm = WorldGuard.getInstance().getPlatform()
                    .getRegionContainer()
                    .get(BukkitAdapter.adapt(fallback.getWorld()));
            if (rm == null) { result.add(fallback); return result; }

            ProtectedRegion region = rm.getRegion(tower.getRegionId());
            if (region == null) { result.add(fallback); return result; }

            for (Player p : plugin.getServer().getOnlinePlayers()) {
                com.sk89q.worldedit.util.Location weLoc = BukkitAdapter.adapt(p.getLocation());
                if (rm.getApplicableRegions(weLoc.toVector().toBlockPoint()).getRegions().contains(region)) {
                    result.add(p);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("잭팟 리전 플레이어 조회 실패: " + e.getMessage());
            result.add(fallback);
        }
        return result.isEmpty() ? List.of(fallback) : result;
    }

    // ──────────────────── 홀로그램 ────────────────────

    public void refreshAllHolograms() {
        if (!plugin.getDhHook().isEnabled()) return;
        for (TowerConfig tower : plugin.getTowerManager().getAllTowers()) {
            updateHologram(tower.getId());
        }
    }

    public void updateHologram(String towerId) {
        if (!plugin.getDhHook().isEnabled()) return;
        TowerConfig tower = plugin.getTowerManager().getTower(towerId);
        if (tower == null) return;
        JackpotTowerConfig jc = tower.getJackpotConfig();
        if (!jc.isEnabled() || jc.getHologramPos() == null) return;

        String gk = tower.getGroupId();
        double amount = jackpotAmounts.getOrDefault(gk, jc.getInitialAmount());
        // 홀로그램 ID는 타워 ID 기반 (타워마다 위치가 다르므로)
        plugin.getDhHook().createOrUpdateHologram(towerId, jc.getHologramPos(), amount);
    }

    /**
     * 같은 그룹에 속한 모든 타워의 홀로그램을 일괄 업데이트.
     * 잭팟 금액이 변경될 때마다 호출.
     */
    private void updateHologramsForGroup(String groupKey) {
        if (!plugin.getDhHook().isEnabled()) return;
        for (TowerConfig tower : plugin.getTowerManager().getAllTowers()) {
            if (tower.getGroupId().equals(groupKey)) {
                updateHologram(tower.getId());
            }
        }
    }

    /** 홀로그램 위치를 타워 yml에 저장하고 재생성 */
    public void setHologramPos(String towerId, org.thetower.tt_luckytower.config.BlockPos pos) {
        plugin.getTowerManager().setHologramPos(towerId, pos);
        plugin.getDhHook().removeHologram(towerId);
        updateHologram(towerId);
    }

    public void removeAllHolograms() {
        if (!plugin.getDhHook().isEnabled()) return;
        for (TowerConfig tower : plugin.getTowerManager().getAllTowers()) {
            plugin.getDhHook().removeHologram(tower.getId());
        }
    }
}
