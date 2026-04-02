package org.thetower.tt_luckytower.config;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;

public class BlockPos {

    private final String worldName;
    private final int x;
    private final int y;
    private final int z;

    public BlockPos(String worldName, int x, int y, int z) {
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Block getBlock() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        return world.getBlockAt(x, y, z);
    }

    public String getWorldName() { return worldName; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }

    @Override
    public String toString() {
        return worldName + "," + x + "," + y + "," + z;
    }

    public static BlockPos fromString(String str) {
        String[] parts = str.split(",");
        if (parts.length != 4) return null;
        try {
            return new BlockPos(parts[0], Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
