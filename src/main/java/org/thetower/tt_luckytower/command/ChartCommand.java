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
        // 손에 든 아이템이 이 타워의 부스트 아이템과 일치하는지 확인
        BoostItem heldBoost = getHeldBoostItem(player, tower);
        double boost = (heldBoost != null) ? heldBoost.getBoost() : 0;

        // ── 헤더 ──
        player.sendMessage("§6§m                                              ");
        player.sendMessage("§6  [ " + tower.getId() + " ]  §7리전: §f" + tower.getRegionId()
                + "  §7입장료: §f" + plugin.getVaultHook().format(tower.getEntryFeeVault()));
        if (boost > 0) {
            player.sendMessage("§e  ※ §f" + heldBoost.getMaterial().name()
                    + " §e부스트 +" + String.format("%.1f", boost) + "% 적용 중");
        }
        // 컬럼 헤더
        player.sendMessage(String.format("§7  %-12s §a%-14s §c%-14s §5%-10s", "층", "성공 확률", "실패 확률", "리셋 확률"));
        player.sendMessage("§7§m                                              ");

        // ── 층별 출력 ──
        for (var entry : tower.getFloors().entrySet()) {
            int floorNum = entry.getKey();
            FloorConfig floor = entry.getValue();

            int rawS = floor.getSuccessChance();
            int rawF = floor.getFailChance();
            int rawR = floor.getResetChance();

            String sVal, fVal, rVal;
            if (boost > 0) {
                int[] eff = calcEffective(floor, boost);
                int deltaS = eff[0] - rawS; // 양수
                int deltaF = eff[1] - rawF; // 음수
                // 성공: 적용확률(+delta%)
                sVal = deltaS > 0 ? eff[0] + "% §8(§a+" + deltaS + "%§8)§a" : rawS + "%";
                // 실패: 적용확률(-delta%)
                fVal = deltaF < 0 ? eff[1] + "% §8(§c" + deltaF + "%§8)§c" : rawF + "%";
                rVal = rawR + "%";
            } else {
                sVal = rawS + "%";
                fVal = rawF + "%";
                rVal = rawR + "%";
            }

            if (floor.isResetFloor()) {
                String label = String.format("★ %d층(리셋)", floorNum);
                player.sendMessage(String.format("§d  %-11s §a%-14s §c%-14s §5%-10s",
                        label, sVal, fVal, rVal));
                if (floor.getJackpotChance() > 0) {
                    player.sendMessage(String.format("§8             └ §6잭팟 §e%.1f%% §6확률 / 풀의 §e%.0f%% §6지급",
                            floor.getJackpotChance(), floor.getJackpotPayoutPercent()));
                }
            } else {
                String label = String.format("  %d층", floorNum);
                player.sendMessage(String.format("§f  %-12s §a%-14s §c%-14s §5%-10s",
                        label, sVal, fVal, rVal));
            }
        }

        // ── 잭팟 현황 ──
        if (tower.getJackpotConfig().isEnabled()) {
            double jackpotAmount = plugin.getJackpotManager().getAmount(tower.getId());
            player.sendMessage("§7§m                                              ");
            player.sendMessage("§6  현재 잭팟: §e" + plugin.getVaultHook().format(jackpotAmount)
                    + " §8(그룹: " + tower.getGroupId() + ")");
        }

        player.sendMessage("§6§m                                              ");
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

    //──────────────────── 리전 자동 감지 ────────────────────

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

    // ──────────────────── 부스트 감지 ────────────────────

    /**
     * 플레이어가 손에 든 아이템이 타워 부스트 아이템과 일치하는지 확인.
     * 일치하고 보유 수량도 충분하면 해당 BoostItem 반환, 아니면 null.
     */
    private BoostItem getHeldBoostItem(Player player, TowerConfig tower) {
        org.bukkit.inventory.ItemStack held = player.getInventory().getItemInMainHand();
        if (held == null || held.getType() == org.bukkit.Material.AIR) return null;
        for (BoostItem bi : tower.getBoostItems()) {
            if (held.getType() != bi.getMaterial()) continue;
            // 인벤토리 전체에서 수량 확인
            int count = 0;
            for (var item : player.getInventory().getContents()) {
                if (item != null && item.getType() == bi.getMaterial()) count += item.getAmount();
            }
            if (count >= bi.getAmount()) return bi;
        }
        return null;
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
