package org.thetower.tt_luckytower.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.potion.PotionEffectType;

public class Reward {

    public enum Type { VAULT, ITEM, MMOITEM, COMMAND, BUFF }

    private final Type type;

    // VAULT
    private double vaultAmount;

    // ITEM
    private Material material;
    private int itemAmount;

    // MMOITEM
    private String mmoItemType;
    private String mmoItemId;

    // COMMAND
    private String command;

    // BUFF
    private PotionEffectType buffType;
    private int buffAmplifier;
    private int buffDurationTicks;

    private Reward(Type type) {
        this.type = type;
    }

    public static Reward fromConfig(ConfigurationSection section) {
        String typeStr = section.getString("type", "ITEM").toUpperCase();
        Type type;
        try {
            type = Type.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            type = Type.ITEM;
        }

        Reward reward = new Reward(type);

        switch (type) {
            case VAULT -> reward.vaultAmount = section.getDouble("amount", 0);
            case ITEM -> {
                String mat = section.getString("material", "STONE");
                reward.material = Material.matchMaterial(mat);
                if (reward.material == null) reward.material = Material.STONE;
                reward.itemAmount = section.getInt("amount", 1);
            }
            case MMOITEM -> {
                reward.mmoItemType = section.getString("mmoitem-type", "");
                reward.mmoItemId = section.getString("mmoitem-id", "");
            }
            case COMMAND -> reward.command = section.getString("command", "");
            case BUFF -> {
                String effectName = section.getString("effect", "SPEED");
                reward.buffType = PotionEffectType.getByName(effectName);
                if (reward.buffType == null) reward.buffType = PotionEffectType.SPEED;
                reward.buffAmplifier = section.getInt("amplifier", 0);
                reward.buffDurationTicks = section.getInt("duration", 60) * 20;
            }
        }

        return reward;
    }

    public Type getType() { return type; }
    public double getVaultAmount() { return vaultAmount; }
    public Material getMaterial() { return material; }
    public int getItemAmount() { return itemAmount; }
    public String getMmoItemType() { return mmoItemType; }
    public String getMmoItemId() { return mmoItemId; }
    public String getCommand() { return command; }
    public PotionEffectType getBuffType() { return buffType; }
    public int getBuffAmplifier() { return buffAmplifier; }
    public int getBuffDurationTicks() { return buffDurationTicks; }
}
