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
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.5f);
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

        // 리셋 층 도달 시 — 직전 구간 누적 보상 즉시 지급 후 초기화 (첫 층 제외)
        if (floor.isResetFloor() && currentFloor != tower.getFloors().firstKey()) {
            if (!pendingRewards.isEmpty()) {
                int count = pendingRewards.size();
                plugin.getRewardManager().giveRewards(player, new ArrayList<>(pendingRewards));
                pendingRewards.clear();
                sendInfo(player, "§6[ 리셋 구간 ] §f" + count + "§7개 보상 지급!");
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.9f, 1.0f);
            }
            lastResetFloor = currentFloor;

            // ── 잭팟 판정 (리셋 층 도달 시) ──
            boolean jackpotWon = plugin.getJackpotManager().rollJackpot(player, tower.getId(), currentFloor);
            if (jackpotWon) {
                sendInfo(player, "§6★ 잭팟 당첨! 게임을 종료합니다. ★");
                endGame(EndReason.COMPLETE);
                return;
            }
        }

        // 램프 업데이트
        plugin.getLightManager().highlightFloor(tower, currentFloor);

        // 타이틀 표시
        player.sendTitle(
                "§e" + currentFloor + "층 판정 중...",
                buildChanceDisplay(floor),
                5, 30, 5
        );
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.8f, 1.2f);

        // 확률 판정
        // 부스트는 실패 확률에서 차감하여 성공 확률로 이전 (전체 합 100% 유지)
        int[] eff = calcEffective(floor);
        int effectiveSuccess = eff[0];
        int effectiveFail    = eff[1];
        // eff[2] = effectiveReset (나머지)

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

        // 최고 층 도달
        if (currentFloor >= tower.getMaxFloor()) {
            player.sendTitle("§6★ ALL CLEAR! ★", "§e모든 층 클리어!", 5, 60, 15);
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.5f);
            sendInfo(player, "§6★ 모든 층 클리어! ★");
            // 금 블럭 위 음표블록(BELL) 5번 반복
            playBellFiveTimes(player);
            endGame(EndReason.COMPLETE);
            return;
        }

        player.sendTitle(
                "§a✔ 성공!",
                "§f" + currentFloor + "층 §7→ §f" + (currentFloor + 1) + "층",
                5, 30, 10
        );
        // 층 올라갈 때 : 기본 성공 사운드 + 음표블록(HARP) 층마다 1 반음씩 상승
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HARP, 1f, getFloorNotePitch(currentFloor));
        sendInfo(player, "§a[ 성공 ] §f" + currentFloor + "층 통과! §7누적 보상: §e" + pendingRewards.size() + "개");

        currentFloor++;
        scheduleNextRoll(-1);
    }

    // ───────── 실패 ─────────
    private void handleFail(Player player, FloorConfig floor) {
        int failRewardRoll = RANDOM.nextInt(100);
        int failFullChance = plugin.getConfig().getInt("settings.fail-full-reward-chance", 80);

        // 0층 실패 전용: 호박 위 음표블록(DIDGERIDOO) 사운드
        if (currentFloor == tower.getFloors().firstKey()) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 1f, 0.5f);
        }

        if (failRewardRoll < failFullChance) {
            // 80% - 누적 보상 전부 수령
            player.sendTitle("§c✘ 실패!", "§f누적 보상을 수령합니다.", 5, 40, 10);
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 0.8f);
            sendInfo(player, "§c[ 실패 ] §7누적 보상 §f" + pendingRewards.size() + "§7개 전부 지급!");
            endGame(EndReason.FAIL_FULL);
        } else {
            // 20% - 현재 층 보상만 수령, 이전 누적 초기화
            List<Reward> currentFloorRewards = new ArrayList<>(floor.getRewards());
            pendingRewards.clear();
            pendingRewards.addAll(currentFloorRewards);
            player.sendTitle("§4✘ 대실패!", "§c이전 보상 초기화! 현재 층 보상만 지급.", 5, 50, 10);
            player.playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.5f, 0.6f);
            sendInfo(player, "§4[ 대실패 ] §c누적 보상 초기화! §7현재 층 보상만 지급.");
            endGame(EndReason.FAIL_PARTIAL);
        }
    }

    // ───────── 리셋 이벤트 ─────────
    private void handleResetEvent(Player player) {
        // 보상은 유지 — 위치만 마지막 리셋 층계로 복귀
        int returnFloor = lastResetFloor;
        player.sendTitle(
                "§5↺ 리셋 이벤트!",
                "§d" + currentFloor + "층 §7→ §d" + returnFloor + "층으로 복귀",
                5, 50, 10
        );
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.5f);
        sendInfo(player, "§5[ 리셋 ] §d" + returnFloor + "층으로 복귀! §7(누적 §f" + pendingRewards.size() + "§7개 유지)");

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
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                sendInfo(player, "§6★ 럭키타워 완전 클리어! 모든 보상 지급 ★");
            }
            case FAIL_FULL -> player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1f, 1f);
            case FAIL_PARTIAL -> player.playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 1f, 0.8f);
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

    // ───────── 사운드 유틸 ─────────

    /**
     * 층마다 1 반음씩 증가하는 음표블록 피치 반환.
     * floor 0 → note 6 (약 0.71), floor 14 → note 20 (약 1.59)
     */
    private float getFloorNotePitch(int floor) {
        int note = Math.min(floor + 6, 24);
        return (float) Math.pow(2.0, note / 12.0) * 0.5f;
    }

    /**
     * 금 블럭 위 음표블록(BELL) 소리를 0.4초 간격으로 5번 재생.
     * 매번 피치가 조금씩 올라가 화려한 완주 효과 연출.
     */
    private void playBellFiveTimes(Player player) {
        new BukkitRunnable() {
            int count = 0;
            @Override
            public void run() {
                Player p = getPlayer();
                if (p == null || count >= 5) { cancel(); return; }
                // 피치: 1.0 → 1.15 → 1.30 → 1.45 → 1.60
                float pitch = 1.0f + count * 0.15f;
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1f, pitch);
                count++;
            }
        }.runTaskTimer(plugin, 0L, 8L); // 8틱(0.4초) 간격
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

        // 실패 확률에서 최대한 차감, 나머지는 버림 (리셋 확률은 변경 안 함)
        int deducted      = Math.min(boost, rawFail);
        int effSuccess    = Math.min(95, rawSuccess + deducted);
        // 성공이 95 캡에 걸린 경우 실제 이전량 재계산
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
