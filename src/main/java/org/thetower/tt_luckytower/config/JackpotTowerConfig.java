package org.thetower.tt_luckytower.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 타워별 잭팟 설정
 */
public class JackpotTowerConfig {

    private final boolean enabled;
    private final double initialAmount;           // 초기 잭팟 금액 (저장된 데이터 없을 때 사용)
    private final double contributionPercent;     // 입장료 중 잭팟 풀에 적립되는 비율 (%)
    private final double lastFloorJackpotPayout;  // 마지막 층 클리어 시 풀에서 지급할 비율 (%)
    private final BlockPos hologramPos;           // 홀로그램 위치
    private final List<String> winnerCommands;    // 당첨자 1명 대상 커맨드 ({player}=당첨자, {amount}=금액)
    private final List<String> regionCommands;    // 리전 내 전원 대상 커맨드 ({player}=각 플레이어, {amount}=금액)
    private final List<String> regionMessages;    // 리전 내 전원에게 보내는 메시지 ({player}=당첨자, {amount}=금액)
    private final List<ItemStack> winnerItems;    // 구역 내 플레이어에게 지급할 아이템

    public JackpotTowerConfig(boolean enabled, double initialAmount, double contributionPercent,
                               double lastFloorJackpotPayout, BlockPos hologramPos,
                               List<String> winnerCommands, List<String> regionCommands,
                               List<String> regionMessages, List<ItemStack> winnerItems) {
        this.enabled = enabled;
        this.initialAmount = initialAmount;
        this.contributionPercent = contributionPercent;
        this.lastFloorJackpotPayout = lastFloorJackpotPayout;
        this.hologramPos = hologramPos;
        this.winnerCommands = winnerCommands;
        this.regionCommands = regionCommands;
        this.regionMessages = regionMessages;
        this.winnerItems = winnerItems;
    }

    public static JackpotTowerConfig fromConfig(ConfigurationSection section) {
        if (section == null) return disabled();

        boolean enabled = section.getBoolean("enabled", false);
        double initialAmount = section.getDouble("initial-amount", 10000.0);
        double contributionPercent = section.getDouble("contribution-percent", 10.0);
        double lastFloorJackpotPayout = section.getDouble("last-floor-payout", 100.0);

        // 홀로그램 위치
        BlockPos hologramPos = null;
        ConfigurationSection holSec = section.getConfigurationSection("hologram");
        if (holSec != null) {
            hologramPos = new BlockPos(
                    holSec.getString("world", "world"),
                    holSec.getInt("x", 0),
                    holSec.getInt("y", 64),
                    holSec.getInt("z", 0)
            );
        }

        // 당첨자 커맨드 (1회, {player}=당첨자)
        List<String> winnerCommands = section.getStringList("winner-rewards.commands");

        // 리전 전원 커맨드 ({player}=각 플레이어, 인당 1회)
        List<String> regionCommands = section.getStringList("winner-rewards.region-commands");

        // 리전 전원 메시지 ({player}=당첨자, {amount}=금액)
        List<String> regionMessages = section.getStringList("winner-rewards.region-messages");

        // 당첨 아이템 (구역 내 모든 플레이어에게 지급)
        List<ItemStack> winnerItems = new ArrayList<>();
        List<?> rawItems = section.getList("winner-rewards.items");
        if (rawItems != null) {
            for (Object obj : rawItems) {
                if (obj instanceof Map<?, ?> map) {
                    Object matObj = map.get("material");
                    String mat = matObj != null ? matObj.toString() : "DIAMOND";
                    Object amountObj = map.get("amount");
                    int amount = amountObj instanceof Number n ? n.intValue() : 1;
                    Material material = Material.matchMaterial(mat);
                    if (material != null) winnerItems.add(new ItemStack(material, amount));
                }
            }
        }

        return new JackpotTowerConfig(enabled, initialAmount, contributionPercent,
                lastFloorJackpotPayout, hologramPos,
                winnerCommands, regionCommands, regionMessages, winnerItems);
    }

    public static JackpotTowerConfig disabled() {
        return new JackpotTowerConfig(false, 0, 0, 100.0, null, List.of(), List.of(), List.of(), List.of());
    }

    public boolean isEnabled() { return enabled; }
    public double getInitialAmount() { return initialAmount; }
    public double getContributionPercent() { return contributionPercent; }
    public double getLastFloorJackpotPayout() { return lastFloorJackpotPayout; }
    public BlockPos getHologramPos() { return hologramPos; }
    public List<String> getWinnerCommands() { return winnerCommands; }
    public List<String> getRegionCommands() { return regionCommands; }
    public List<String> getRegionMessages() { return regionMessages; }
    public List<ItemStack> getWinnerItems() { return winnerItems; }
}
