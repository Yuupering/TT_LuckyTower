package org.thetower.tt_luckytower.manager;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Lightable;
import org.thetower.tt_luckytower.LuckyTowerPlugin;
import org.thetower.tt_luckytower.config.BlockPos;
import org.thetower.tt_luckytower.config.FloorConfig;
import org.thetower.tt_luckytower.config.TowerConfig;

public class LightManager {

    private final LuckyTowerPlugin plugin;

    public LightManager(LuckyTowerPlugin plugin) {
        this.plugin = plugin;
    }

    /** 특정 층 램프 켜기 */
    public void lightFloor(FloorConfig floor, boolean on) {
        for (BlockPos pos : floor.getLights()) {
            Block block = pos.getBlock();
            if (block == null) continue;
            setLamp(block, on);
        }
    }

    /** 타워의 모든 램프 끄기 */
    public void resetAllLights(TowerConfig tower) {
        for (FloorConfig floor : tower.getFloors().values()) {
            lightFloor(floor, false);
        }
    }

    /** 현재 진행 층만 켜고 나머지는 끄기 */
    public void highlightFloor(TowerConfig tower, int currentFloor) {
        for (FloorConfig floor : tower.getFloors().values()) {
            boolean on = floor.getFloorNumber() == currentFloor;
            lightFloor(floor, on);
        }
    }

    /** 성공한 층까지 모두 켜기 */
    public void lightUpToFloor(TowerConfig tower, int upToFloor) {
        for (FloorConfig floor : tower.getFloors().values()) {
            boolean on = floor.getFloorNumber() <= upToFloor;
            lightFloor(floor, on);
        }
    }

    private void setLamp(Block block, boolean on) {
        // 블록이 REDSTONE_LAMP가 아니면 변환
        if (block.getType() != Material.REDSTONE_LAMP) {
            block.setType(Material.REDSTONE_LAMP);
        }
        Lightable lightable = (Lightable) block.getBlockData();
        if (lightable.isLit() == on) return; // 이미 같은 상태
        lightable.setLit(on);
        block.setBlockData(lightable);
    }
}
