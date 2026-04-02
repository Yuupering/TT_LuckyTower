package org.thetower.tt_luckytower.config;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class TowerConfig {

    private final String id;
    private final String groupId;    // 잭팟 공유 그룹 ID (없으면 tower ID 사용)
    private final String regionId;
    private final BlockPos interactionBlock;
    private final double entryFeeVault;
    private final List<BoostItem> boostItems;
    private final TreeMap<Integer, FloorConfig> floors; // 정렬된 층 맵
    private final JackpotTowerConfig jackpotConfig;

    public TowerConfig(String id, String groupId, String regionId, BlockPos interactionBlock,
                       double entryFeeVault, List<BoostItem> boostItems,
                       TreeMap<Integer, FloorConfig> floors, JackpotTowerConfig jackpotConfig) {
        this.id = id;
        this.groupId = (groupId != null && !groupId.isBlank()) ? groupId : id;
        this.regionId = regionId;
        this.interactionBlock = interactionBlock;
        this.entryFeeVault = entryFeeVault;
        this.boostItems = boostItems;
        this.floors = floors;
        this.jackpotConfig = jackpotConfig;
    }

    public static TowerConfig fromConfig(String id, ConfigurationSection section) {
        String group = section.getString("group", id);
        String region = section.getString("region", "lucky_tower");

        // 상호작용 블록
        BlockPos interactionBlock = null;
        ConfigurationSection ibSection = section.getConfigurationSection("interaction-block");
        if (ibSection != null) {
            String world = ibSection.getString("world", "world");
            int x = ibSection.getInt("x", 0);
            int y = ibSection.getInt("y", 64);
            int z = ibSection.getInt("z", 0);
            interactionBlock = new BlockPos(world, x, y, z);
        }

        double entryFeeVault = section.getDouble("entry-fee-vault", 0);

        // 부스트 아이템
        List<BoostItem> boostItems = new ArrayList<>();
        List<?> rawBoosts = section.getList("boost-items");
        if (rawBoosts != null) {
            for (Object obj : rawBoosts) {
                if (obj instanceof Map<?, ?> map) {
                    org.bukkit.configuration.MemoryConfiguration mc = new org.bukkit.configuration.MemoryConfiguration();
                    for (Map.Entry<?, ?> entry : map.entrySet()) {
                        mc.set(entry.getKey().toString(), entry.getValue());
                    }
                    boostItems.add(BoostItem.fromConfig(mc));
                }
            }
        }

        // 층 설정
        TreeMap<Integer, FloorConfig> floors = new TreeMap<>();
        ConfigurationSection floorsSection = section.getConfigurationSection("floors");
        if (floorsSection != null) {
            for (String key : floorsSection.getKeys(false)) {
                try {
                    int floorNum = Integer.parseInt(key);
                    ConfigurationSection floorSection = floorsSection.getConfigurationSection(key);
                    if (floorSection != null) {
                        floors.put(floorNum, FloorConfig.fromConfig(floorNum, floorSection));
                    }
                } catch (NumberFormatException ignored) {}
            }
        }

        // 잭팟 설정
        JackpotTowerConfig jackpotConfig = JackpotTowerConfig.fromConfig(
                section.getConfigurationSection("jackpot"));

        return new TowerConfig(id, group, region, interactionBlock, entryFeeVault, boostItems, floors, jackpotConfig);
    }

    public int getMaxFloor() {
        return floors.isEmpty() ? 0 : floors.lastKey();
    }

    public FloorConfig getFloor(int floorNumber) {
        return floors.get(floorNumber);
    }

    public FloorConfig getFirstFloor() {
        return floors.isEmpty() ? null : floors.firstEntry().getValue();
    }

    /** 현재 층보다 낮은 가장 가까운 리셋 층계 번호 반환 (없으면 첫 번째 층) */
    public int getNearestResetFloorBelow(int currentFloor) {
        for (int f = currentFloor - 1; f >= floors.firstKey(); f--) {
            FloorConfig fc = floors.get(f);
            if (fc != null && fc.isResetFloor()) return f;
        }
        return floors.firstKey();
    }

    public String getId() { return id; }
    /** 잭팟 공유 그룹 키. group 미설정 시 타워 ID와 동일. */
    public String getGroupId() { return groupId; }
    public String getRegionId() { return regionId; }
    public BlockPos getInteractionBlock() { return interactionBlock; }
    public double getEntryFeeVault() { return entryFeeVault; }
    public List<BoostItem> getBoostItems() { return boostItems; }
    public TreeMap<Integer, FloorConfig> getFloors() { return floors; }
    public JackpotTowerConfig getJackpotConfig() { return jackpotConfig; }
}
