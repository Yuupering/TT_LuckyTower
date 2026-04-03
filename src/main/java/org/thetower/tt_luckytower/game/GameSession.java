package org.thetower.tt_luckytower.game;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.thetower.tt_luckytower.LuckyTowerPlugin;
import org.thetower.tt_luckytower.config.FloorConfig;
import org.thetower.tt_luckytower.config.RegionBuff;
import org.thetower.tt_luckytower.config.Reward;
import org.thetower.tt_luckytower.config.TowerConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public class GameSession {

    public enum EndReason { FAIL_FULL, FAIL_PARTIAL, RESET_EVENT, COMPLETE, FORCE_STOP }

    private final LuckyTowerPlugin plugin;
    private final UUID playerId;
    private final TowerConfig tower;
    private final double boostPercent;    // 부스트 아이템에 의한 확률 증가값 (예: 8.0 = +8%)

    private int currentFloor;
    private int lastResetFloor;           // 가장 최근에 통과한 리셋 층 번호
    private final List<Reward> pendingRewards = new ArrayList<>();
    private boolean active = false;
    private BukkitTask gameTask;

    private static final Random RANDOM = new Random();

    public GameSession(LuckyTowerPlugin plugin, UUID playerId, TowerConfig tower, double boostPercent) {
        this.plugin = plugin;
        this.playerId = playerId;
        this.tower = tower;
        this.boostPercent = boostPercent;
        this.currentFloor = tower.getFloors().firstKey();
        this.lastResetFloor = currentFloor;
    }

    public void start() {
        active = true;
        Player player = getPlayer();
        if (player == null) { endGame(EndReason.FORCE_STOP); return; }

        player.sendTitle("§6럭키 타워", "§e" + tower.getId() + " §7| §f도전 시작!", 10, 40, 10);
        playSoundInRegion(Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.5f);
        sendInfo(player, "§6[ 럭키타워 시작 ] §r§e" + tower.getId());
        sendInfo(player, "§7부스트: §f+" + String.format("%.1f", boostPercent) + "% §8| §7총 §f" + tower.getFloors().size() + "§7층");

        int startDelay = plugin.getConfig().getInt("settings.start-delay-ticks", 60);
        scheduleNextRoll(startDelay);
    }

    private void scheduleNextRoll(int delayTicks) {
        int delay = (delayTicks > 0) ? delayTicks : plugin.getConfig().getInt("settings.floor-delay-ticks", 40);
        gameTask = plugin.getServer().getScheduler().runTaskLater(plugin, this::rollCurrentFloor, delay);
    }

    private void rollCurrentFloor() {
        if (!active) return;

        Player player = getPlayer();
        if (player == null) { endGame(EndReason.FORCE_STOP); return; }

        FloorConfig floor = tower.getFloor(currentFloor);
        if (floor == null) { endGame(EndReason.FORCE_STOP); return; }

        // 램프 업데이트
        plugin.getLightManager().highlightFloor(tower, currentFloor);

        // 타이틀 표시
        player.sendTitle(
                "§e" + currentFloor + "층 판정 중...",
                buildChanceDisplay(floor),
                5, 30, 5
        );
        playSoundInRegion(Sound.BLOCK_NOTE_BLOCK_HAT, 0.8f, 1.2f);

        // 확률 판정
        int[] eff = calcEffective(floor);
        int effectiveSuccess = eff[0];
        int effectiveFail    = eff[1];

        int roll = RANDOM.nextInt(100); // 0~99

        if (roll < effectiveSuccess) {
            handleSuccess(player, floor);
        } else if (roll < effectiveSuccess + effectiveFail) {
            handleFail(player, floor);
        } else {
            handleResetEvent(player);
        }
    }

    // ───────── 성공 ─────────
    private void handleSuccess(Player player, FloorConfig floor) {
        pendingRewards.addAll(floor.getRewards());

        // 리전 버프 적용
        applyRegionBuffs(floor);

        // 리셋 층계 통과 → 체크포인트 갱신 (보상 지급 없음, 게임 종료 시 일괄 지급)
        if (floor.isResetFloor()) {
            lastResetFloor = currentFloor;
            sendInfo(player, "§6[ 체크포인트 ] §f" + currentFloor + "층 통과! §7(누적 §e" + pendingRewards.size() + "§7개)");
        }

        // 최고 층 도달
        if (currentFloor >= tower.getMaxFloor()) {
            player.sendTitle("§6★ ALL CLEAR! ★", "§e모든 층 클리어!", 5, 60, 15);
            playSoundInRegion(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.5f);
            sendInfo(player, "§6★ 모든 층 클리어! ★");
            playBellFiveTimes();
            // 마지막 층 클리어 → 잭팟 100% 자동 발동
            if (tower.getJackpotConfig().isEnabled()) {
                plugin.getJackpotManager().triggerLastFloorJackpot(player, tower.getId());
            }
            endGame(EndReason.COMPLETE);
            return;
        }

        player.sendTitle(
                "§a✔ 성공!",
                "§f" + currentFloor + "층 §7→ §f" + (currentFloor + 1) + "층",
                5, 30, 10
        );
        playSoundInRegion(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
        playSoundInRegion(Sound.BLOCK_NOTE_BLOCK_HARP, 1f, getFloorNotePitch(currentFloor));
        sendInfo(player, "§a[ 성공 ] §f" + currentFloor + "층 통과! §7누적 보상: §e" + pendingRewards.size() + "개");

        currentFloor++;
        scheduleNextRoll(-1);
    }

    // ───────── 실패 ─────────
    private void handleFail(Player player, FloorConfig floor) {
        // 0층 실패 전용: 호박 위 음표블록(DIDGERIDOO) 사운드
        if (currentFloor == tower.getFloors().firstKey()) {
            playSoundInRegion(Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 1f, 0.5f);
        }

        // ── ① 잭팟 판정: 리셋 층계 fail이면 행운/대실패와 무관하게 항상 체크 ──
        if (floor.isResetFloor() && tower.getJackpotConfig().isEnabled()) {
            if (plugin.getJackpotManager().rollJackpot(player, tower.getId(), currentFloor)) {
                sendInfo(player, "§6[ 잭팟 ] §f실패했지만 §6★ 잭팟 당첨! ★");
                endGame(EndReason.FAIL_PARTIAL); // 잭팟 당첨이면 보상도 지급
                return;
            }
        }

        // ── ② fail-full-reward-chance 판정: 모든 실패에 적용 ──
        int fullRewardChance = plugin.getConfig().getInt("settings.fail-full-reward-chance", 80);
        int roll = RANDOM.nextInt(100); // 0~99

        if (roll < fullRewardChance) {
            // 실패: 누적 보상 획득
            player.sendTitle("§c✘ 실패!", "§7누적 보상을 수령합니다.", 5, 40, 10);
            playSoundInRegion(Sound.BLOCK_GLASS_BREAK, 1f, 0.8f);
            sendInfo(player, "§c[ 실패 ] §7도전 실패! 누적 보상 지급.");
            endGame(EndReason.FAIL_PARTIAL);

        } else {
            // 대실패: 현재 층 보상만 지급 후 누적 보상 전부 초기화
            List<Reward> currentFloorReward = new ArrayList<>(floor.getRewards());
            pendingRewards.clear();
            pendingRewards.addAll(currentFloorReward);

            player.sendTitle("§4✘ 대실패!", "§c현재 층 보상만 획득 후 초기화됩니다.", 5, 50, 10);
            playSoundInRegion(Sound.ENTITY_VILLAGER_NO, 1f, 0.6f);
            sendInfo(player, "§c[ 대실패 ] §f현재 층 보상만 지급 후 누적 보상 초기화.");
            endGame(EndReason.FAIL_FULL);
        }
    }

    // ───────── 리셋 이벤트 ─────────
    private void handleResetEvent(Player player) {
        int returnFloor = lastResetFloor;

        pendingRewards.clear();
        player.sendTitle(
                "§5↺ 리셋 이벤트!",
                "§d" + currentFloor + "층 §7→ §d" + returnFloor + "층으로 복귀 §c(보상 초기화)",
                5, 50, 10
        );
        playSoundInRegion(Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.5f);
        sendInfo(player, "§5[ 리셋 ] §d" + returnFloor + "층으로 복귀! §c(모든 누적 보상 초기화)");

        currentFloor = returnFloor;
        scheduleNextRoll(-1);
    }

    // ───────── 게임 종료 ─────────
    public void endGame(EndReason reason) {
        if (!active) return;
        active = false;

        if (gameTask != null) {
            gameTask.cancel();
            gameTask = null;
        }

        plugin.getLightManager().resetAllLights(tower);
        plugin.getGameManager().removeSession(playerId);

        Player player = getPlayer();
        if (player == null) return;

        switch (reason) {
            case COMPLETE -> {
                playSoundInRegion(Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                sendInfo(player, "§6★ 럭키타워 완전 클리어! 모든 보상 지급 ★");
            }
            case FAIL_FULL -> {
                playSoundInRegion(Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.5f, 0.8f);
                playSoundInRegion(Sound.ENTITY_VILLAGER_NO, 1f, 0.5f);
            }
            case FAIL_PARTIAL -> playSoundInRegion(Sound.BLOCK_GLASS_BREAK, 1f, 0.8f);
            case RESET_EVENT -> {}
            case FORCE_STOP -> {
                sendInfo(player, "§c게임이 강제 종료되었습니다. 보상 미지급.");
                return;
            }
        }

        // 보상 지급
        if (!pendingRewards.isEmpty()) {
            sendInfo(player, "§a[ 보상 지급 ] §r§f총 §e" + pendingRewards.size() + "§f개");
            plugin.getRewardManager().giveRewards(player, pendingRewards);
        } else {
            sendInfo(player, "§7지급할 보상이 없습니다.");
        }
    }

    // ───────── 리전 버프 ─────────
    private void applyRegionBuffs(FloorConfig floor) {
        if (floor.getRegionBuffs().isEmpty()) return;
        try {
            RegionManager rm = WorldGuard.getInstance().getPlatform()
                    .getRegionContainer()
                    .get(BukkitAdapter.adapt(getPlayer().getWorld()));
            if (rm == null) return;

            ProtectedRegion region = rm.getRegion(tower.getRegionId());
            if (region == null) return;

            for (Player p : plugin.getServer().getOnlinePlayers()) {
                com.sk89q.worldedit.util.Location weLoc = BukkitAdapter.adapt(p.getLocation());
                if (rm.getApplicableRegions(weLoc.toVector().toBlockPoint())
                        .getRegions().contains(region)) {
                    for (RegionBuff buff : floor.getRegionBuffs()) {
                        p.addPotionEffect(new PotionEffect(
                                buff.getType(), buff.getDurationTicks(),
                                buff.getAmplifier(), false, true, true
                        ));
                    }
                    p.sendMessage("§d[럭키타워] §f" + currentFloor + "층 달성! §e특별 버프 적용!");
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("리전 버프 적용 실패: " + e.getMessage());
        }
    }

    // ───────── 리전 내 플레이어 목록 ─────────

    private List<Player> getRegionPlayers() {
        List<Player> result = new ArrayList<>();
        try {
            Player self = getPlayer();
            if (self == null) return result;

            RegionManager rm = WorldGuard.getInstance().getPlatform()
                    .getRegionContainer()
                    .get(BukkitAdapter.adapt(self.getWorld()));
            if (rm == null) return result;

            ProtectedRegion region = rm.getRegion(tower.getRegionId());
            if (region == null) return result;

            for (Player p : plugin.getServer().getOnlinePlayers()) {
                if (!p.getWorld().equals(self.getWorld())) continue;
                com.sk89q.worldedit.util.Location weLoc = BukkitAdapter.adapt(p.getLocation());
                if (rm.getApplicableRegions(weLoc.toVector().toBlockPoint())
                        .getRegions().contains(region)) {
                    result.add(p);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("리전 플레이어 조회 실패: " + e.getMessage());
            // fallback: 세션 플레이어만
            Player self = getPlayer();
            if (self != null) result.add(self);
        }
        return result;
    }

    /**
     * 리전 내 모든 플레이어에게 사운드 재생.
     * 각 플레이어 위치에서 재생하므로 거리 감쇠 없이 들림.
     */
    private void playSoundInRegion(Sound sound, float volume, float pitch) {
        for (Player p : getRegionPlayers()) {
            p.playSound(p.getLocation(), sound, volume, pitch);
        }
        // 리전 밖에 있어도 세션 플레이어 본인은 반드시 들림
        Player self = getPlayer();
        if (self != null && !getRegionPlayers().contains(self)) {
            self.playSound(self.getLocation(), sound, volume, pitch);
        }
    }

    // ───────── 사운드 유틸 ─────────

    /**
     * 층마다 1 반음씩 증가하는 음표블록 피치 반환.
     */
    private float getFloorNotePitch(int floor) {
        int note = Math.min(floor + 6, 24);
        return (float) Math.pow(2.0, note / 12.0) * 0.5f;
    }

    /**
     * 금 블럭 위 음표블록(BELL) 소리를 0.4초 간격으로 5번 재생 (리전 내 전체).
     */
    private void playBellFiveTimes() {
        // 재생 시점의 리전 플레이어 스냅샷
        List<Player> regionPlayers = getRegionPlayers();
        new BukkitRunnable() {
            int count = 0;
            @Override
            public void run() {
                if (count >= 5) { cancel(); return; }
                float pitch = 1.0f + count * 0.15f;
                for (Player p : regionPlayers) {
                    if (p.isOnline()) {
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1f, pitch);
                    }
                }
                count++;
            }
        }.runTaskTimer(plugin, 0L, 8L);
    }

    // ───────── 유틸 ─────────

    /**
     * 부스트를 실패 확률에서 차감해 성공 확률로 이전.
     * @return [effectiveSuccess, effectiveFail, effectiveReset]
     */
    int[] calcEffective(FloorConfig floor) {
        int boost      = (int) boostPercent;
        int rawSuccess = floor.getSuccessChance();
        int rawFail    = floor.getFailChance();

        int deducted      = Math.min(boost, rawFail);
        int effSuccess    = Math.min(95, rawSuccess + deducted);
        int actualGain    = effSuccess - rawSuccess;
        int effFail       = rawFail - actualGain;
        int effReset      = 100 - effSuccess - effFail;

        return new int[]{effSuccess, effFail, effReset};
    }

    private String buildChanceDisplay(FloorConfig floor) {
        int[] eff = calcEffective(floor);
        String base = "§a성공 " + eff[0] + "% §7| §c실패 " + eff[1] + "% §7| §5리셋 " + eff[2] + "%";
        if (boostPercent > 0) {
            base += " §8(부스트 §a+" + String.format("%.0f", boostPercent) + "%§8)";
        }
        return base;
    }

    private void sendInfo(Player player, String msg) {
        player.sendMessage("§8[§6LuckyTower§8] §r" + msg);
    }

    private Player getPlayer() {
        return plugin.getServer().getPlayer(playerId);
    }

    public UUID getPlayerId() { return playerId; }
    public TowerConfig getTower() { return tower; }
    public int getCurrentFloor() { return currentFloor; }
    public boolean isActive() { return active; }
    public List<Reward> getPendingRewards() { return pendingRewards; }
}
