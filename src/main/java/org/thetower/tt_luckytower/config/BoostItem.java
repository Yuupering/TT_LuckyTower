package org.thetower.tt_luckytower.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

public class BoostItem {

    private final Material material;
    private final int amount;
    private final double boost; // 성공 확률 증가량 (예: 5.0 = +5%)

    public BoostItem(Material material, int amount, double boost) {
        this.material = material;
        this.amount = amount;
        this.boost = boost;
    }

    public static BoostItem fromConfig(ConfigurationSection section) {
        String mat = section.getString("material", "IRON_BLOCK");
        Material material = Material.matchMaterial(mat);
        if (material == null) material = Material.IRON_BLOCK;
        int amount = section.getInt("amount", 1);
        double boost = section.getDouble("boost", 0.0);
        return new BoostItem(material, amount, boost);
    }

    public Material getMaterial() { return material; }
    public int getAmount() { return amount; }
    public double getBoost() { return boost; }
}
