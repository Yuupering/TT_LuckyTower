package org.thetower.tt_luckytower.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.potion.PotionEffectType;

public class RegionBuff {

    private final PotionEffectType type;
    private final int amplifier;
    private final int durationTicks;

    public RegionBuff(PotionEffectType type, int amplifier, int durationTicks) {
        this.type = type;
        this.amplifier = amplifier;
        this.durationTicks = durationTicks;
    }

    public static RegionBuff fromConfig(ConfigurationSection section) {
        String effectName = section.getString("effect", "SPEED");
        PotionEffectType type = PotionEffectType.getByName(effectName);
        if (type == null) type = PotionEffectType.SPEED;
        int amplifier = section.getInt("amplifier", 0);
        int durationTicks = section.getInt("duration", 60) * 20;
        return new RegionBuff(type, amplifier, durationTicks);
    }

    public PotionEffectType getType() { return type; }
    public int getAmplifier() { return amplifier; }
    public int getDurationTicks() { return durationTicks; }
}
