package org.thetower.tt_luckytower.config;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

public class FloorConfig {

    private final int floorNumber;
    private final boolean resetFloor;      // 도달 시 누적 보상 자동 초기화
    private final int successChance;       // 성공 확률 (0~100)
    private final int failChance;          // 실패 확률 (0~100)
    private final int resetChance;         // 리셋 이벤트 확률 (0~100)
    private final List<Reward> rewards;
    private final List<RegionBuff> regionBuffs; // 이 층 도달 시 리전 내 플레이어에게 버프
    private final List<BlockPos> lights;        // 레드스톤 램프 좌표
    private final double jackpotChance;    // 잭팟 발동 확률 % (reset-floor 인 층에서만 유효)
    private final double jackpotPayoutPercent; // 잭팟 풀 중 지급 비율 %

    public FloorConfig(int floorNumber, boolean resetFloor,
                       int successChance, int failChance, int resetChance,
                       List<Reward> rewards, List<RegionBuff> regionBuffs, List<BlockPos> lights,
                       double jackpotChance, double jackpotPayoutPercent) {
        this.floorNumber = floorNumber;
        this.resetFloor = resetFloor;
        this.successChance = successChance;
        this.failChance = failChance;
        this.resetChance = resetChance;
        this.rewards = rewards;
        this.regionBuffs = regionBuffs;
        this.lights = lights;
        this.jackpotChance = jackpotChance;
        this.jackpotPayoutPercent = jackpotPayoutPercent;
    }

    public static FloorConfig fromConfig(int floorNumber, ConfigurationSection section) {
        boolean resetFloor = section.getBoolean("reset-floor", false);
        int success = section.getInt("success", 75);
        int fail = section.getInt("fail", 20);
        int reset = section.getInt("reset", 5);

        // 합이 100이 넘으면 비율 정규화
        int total = success + fail + reset;
        if (total != 100) {
            double factor = 100.0 / total;
            success = (int) (success * factor);
            fail = (int) (fail * factor);
            reset = 100 - success - fail;
        }

        List<Reward> rewards = new ArrayList<>();
        if (section.isList("rewards")) {
            for (Object obj : section.getList("rewards")) {
                if (obj instanceof ConfigurationSection cs) {
                    rewards.add(Reward.fromConfig(cs));
                }
            }
        } else if (section.isConfigurationSection("rewards")) {
            // 맵 형태일 경우 처리
            ConfigurationSection rewardSection = section.getConfigurationSection("rewards");
            if (rewardSection != null) {
                for (String key : rewardSection.getKeys(false)) {
                    ConfigurationSection rs = rewardSection.getConfigurationSection(key);
                    if (rs != null) rewards.add(Reward.fromConfig(rs));
                }
            }
        }
        // YAML 리스트를 올바르게 파싱
        List<?> rawRewards = section.getList("rewards");
        if (rawRewards != null) {
            rewards.clear();
            for (Object obj : rawRewards) {
                if (obj instanceof java.util.Map<?, ?> map) {
                    ConfigurationSection cs = mapToSection(section, map);
                    rewards.add(Reward.fromConfig(cs));
                }
            }
        }

        List<RegionBuff> buffs = new ArrayList<>();
        List<?> rawBuffs = section.getList("region-buffs");
        if (rawBuffs != null) {
            for (Object obj : rawBuffs) {
                if (obj instanceof java.util.Map<?, ?> map) {
                    ConfigurationSection cs = mapToSection(section, map);
                    buffs.add(RegionBuff.fromConfig(cs));
                }
            }
        }

        List<BlockPos> lights = new ArrayList<>();
        List<String> lightStrings = section.getStringList("lights");
        for (String s : lightStrings) {
            BlockPos pos = BlockPos.fromString(s);
            if (pos != null) lights.add(pos);
        }

        double jackpotChance = section.getDouble("jackpot-chance", 0.0);
        double jackpotPayout = section.getDouble("jackpot-payout", 50.0);

        return new FloorConfig(floorNumber, resetFloor, success, fail, reset,
                rewards, buffs, lights, jackpotChance, jackpotPayout);
    }

    @SuppressWarnings("unchecked")
    private static ConfigurationSection mapToSection(ConfigurationSection parent, java.util.Map<?, ?> map) {
        org.bukkit.configuration.MemoryConfiguration mc = new org.bukkit.configuration.MemoryConfiguration();
        for (java.util.Map.Entry<?, ?> entry : map.entrySet()) {
            mc.set(entry.getKey().toString(), entry.getValue());
        }
        return mc;
    }

    public int getFloorNumber() { return floorNumber; }
    public boolean isResetFloor() { return resetFloor; }
    public int getSuccessChance() { return successChance; }
    public int getFailChance() { return failChance; }
    public int getResetChance() { return resetChance; }
    public List<Reward> getRewards() { return rewards; }
    public List<RegionBuff> getRegionBuffs() { return regionBuffs; }
    public List<BlockPos> getLights() { return lights; }
    public double getJackpotChance() { return jackpotChance; }
    public double getJackpotPayoutPercent() { return jackpotPayoutPercent; }
}
