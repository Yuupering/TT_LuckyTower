package org.thetower.tt_luckytower.command;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.thetower.tt_luckytower.LuckyTowerPlugin;
import org.thetower.tt_luckytower.config.BoostItem;
import org.thetower.tt_luckytower.config.FloorConfig;
import org.thetower.tt_luckytower.config.TowerConfig;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * /확률표
 *
 * 사용법:
 *   /확률표           - 현재 서 있는 리전의 모든 타워 확률 표시
 *   /확률표 <타워ID>  - 특정 타워 확률 표시
 */
public class ChartCommand implements CommandExecutor, TabCompleter {

    private final LuckyTowerPlugin plugin;

    public ChartCommand(LuckyTowerPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c플레이어만 사용 가능합니다.");
            return true;
        }

        if (args.length >= 1) {
            // /확률표 <타워ID>
            TowerConfig tower = plugin.getTowerManager().getTower(args[0]);
            if (tower == null) {
                player.sendMessage("§c타워를 찾을 수 없습니다: §f" + args[0]);
                return true;
            }
            printChart(player, tower);
        } else {
            // /확률표 — 현재 리전 자동 감지
            List<TowerConfig> found = getTowersAtPlayer(player);
            if (found.isEmpty()) {
                player.sendMessage("§c현재 위치에서 럭키타워 리전을 찾을 수 없습니다.");
                player.sendMessage("§7특정 타워를 보려면: §f/확률표 <타워ID>");
                return true;
            }
            for (TowerConfig tower : found) {
                printChart(player, tower);
            }
        }
        return true;
    }

    // ──────────────────── 확률표 출력 ────────────────────

    private void printChart(Player player, TowerConfig tower) {
        // 플레이어 보유 부스트 계산 (소모 안 함)
        double boost = calcPlayerBoost(player, tower);

        // ── 헤더 ──
        player.sendMessage("§6━━━━━━ [ " + tower.getId() + " 확률표 ] ━━━━━━");
        player.sendMessage("§7리전: §f" + tower.getRegionId()
                + "  §7그룹: §f" + tower.getGroupId()
                + "  §7입장료: §f" + plugin.getVaultHook().format(tower.getEntryFeeVault()));

        if (boost > 0) {
            player.sendMessage("§e※ 부스트 §a+" + String.format("%.1f", boost)
                    + "% §e보유 중 → 실패 확률에서 차감, 성공 확률 증가 (1판 소모)");
            player.sendMessage("§7   (괄호 안 = 부스트 적용 시 확률)");
        }

        player.sendMessage("§7§m                                        ");

        // ── 층별 출력 ──
        for (var entry : tower.getFloors().entrySet()) {
            int floorNum = entry.getKey();
            FloorConfig floor = entry.getValue();

            int rawS = floor.getSuccessChance();
            int rawF = floor.getFailChance();
            int rawR = floor.getResetChance();

            StringBuilder line = new StringBuilder();

            // 층 번호 & 리셋 구간 표시
            if (floor.isResetFloor()) {
                line.append("§d[리셋]");
            } else {
                line.append("§7      ");
            }
            line.append(String.format("§f %2d층  ", floorNum));

            if (boost > 0) {
                int[] eff = calcEffective(floor, boost);
                // 기본값이 달라진 항목은 색상으로 강조
                line.append(formatStat("§a성공", rawS, eff[0]));
                line.append("  ");
                line.append(formatStat("§c실패", rawF, eff[1]));
                line.append("  ");
                line.append(formatStat("§5리셋", rawR, eff[2]));
            } else {
                line.append("§a성공 §f").append(rawS).append("%");
                line.append("  §c실패 §f").append(rawF).append("%");
                line.append("  §5리셋 §f").append(rawR).append("%");
            }

            // 잭팟 정보 (리셋 층만)
            if (floor.isResetFloor() && floor.getJackpotChance() > 0) {
                line.append(String.format("  §6잭팟 §e%.1f%%§6(풀의 §e%.0f%%§6 지급)",
                        floor.getJackpotChance(), floor.getJackpotPayoutPercent()));
            }

            player.sendMessage(line.toString());
        }

        // ── 잭팟 현황 ──
        if (tower.getJackpotConfig().isEnabled()) {
            double jackpotAmount = plugin.getJackpotManager().getAmount(tower.getId());
            player.sendMessage("§7§m                                        ");
            player.sendMessage("§6현재 잭팟: §e"
                    + plugin.getVaultHook().format(jackpotAmount)
                    + " §8(그룹: " + tower.getGroupId() + ")");
        }

        player.sendMessage("§7§m                                        ");
    }

    /**
     * 부스트 적용 시 확률 계산.
     * GameSession.calcEffective 와 동일 로직.
     */
    private static int[] calcEffective(FloorConfig floor, double boostPercent) {
        int boost      = (int) boostPercent;
        int rawSuccess = floor.getSuccessChance();
        int rawFail    = floor.getFailChance();
        int deducted   = Math.min(boost, rawFail);
        int effSuccess = Math.min(95, rawSuccess + deducted);
        int actualGain = effSuccess - rawSuccess;
        int effFail    = rawFail - actualGain;
        int effReset   = 100 - effSuccess - effFail;
        return new int[]{effSuccess, effFail, effReset};
    }

    /**
     * 변경 여부에 따라 색상 강조.
     * 예: "§a성공 §f75%§8(→83%)"
     */
    private static String formatStat(String label, int base, int effective) {
        if (base == effective) {
            return label + " §f" + base + "%";
        }
        return label + " §8" + base + "%§7(→§f" + effective + "§7%)";
    }

    // ──────────────────── 리전 자동 감지 ────────────────────

    private List<TowerConfig> getTowersAtPlayer(Player player) {
        try {
            RegionManager rm = WorldGuard.getInstance().getPlatform()
                    .getRegionContainer()
                    .get(BukkitAdapter.adapt(player.getWorld()));
            if (rm == null) return List.of();

            ApplicableRegionSet applicable = rm.getApplicableRegions(
                    BukkitAdapter.adapt(player.getLocation()).toVector().toBlockPoint());

            // 플레이어가 속한 리전 ID 목록
            Set<String> regionIds = applicable.getRegions().stream()
                    .map(ProtectedRegion::getId)
                    .collect(Collectors.toSet());

            // 해당 리전에 연결된 타워 수집
            return regionIds.stream()
                    .flatMap(rid -> plugin.getTowerManager().getTowersByRegion(rid).stream())
                    .distinct()
                    .collect(Collectors.toList());

        } catch (Exception e) {
            plugin.getLogger().warning("확률표 리전 조회 실패: " + e.getMessage());
            return List.of();
        }
    }

    // ──────────────────── 부스트 계산 ────────────────────

    /** 플레이어가 보유한 부스트 아이템 합산 (소모하지 않음) */
    private double calcPlayerBoost(Player player, TowerConfig tower) {
        double total = 0;
        for (BoostItem bi : tower.getBoostItems()) {
            int count = 0;
            for (var item : player.getInventory().getContents()) {
                if (item != null && item.getType() == bi.getMaterial()) count += item.getAmount();
            }
            if (count >= bi.getAmount()) total += bi.getBoost();
        }
        return total;
    }

    // ──────────────────── 탭 완성 ────────────────────

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return plugin.getTowerManager().getAllTowers().stream()
                    .map(TowerConfig::getId)
                    .filter(id -> id.startsWith(args[0]))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
