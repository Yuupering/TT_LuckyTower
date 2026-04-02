package org.thetower.tt_luckytower.hook;

import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import org.bukkit.Location;
import org.thetower.tt_luckytower.LuckyTowerPlugin;
import org.thetower.tt_luckytower.config.BlockPos;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

public class DecentHologramsHook {

    private final LuckyTowerPlugin plugin;
    private boolean enabled = false;

    public DecentHologramsHook(LuckyTowerPlugin plugin) {
        this.plugin = plugin;
    }

    public void setup() {
        if (plugin.getServer().getPluginManager().getPlugin("DecentHolograms") != null) {
            enabled = true;
            plugin.getLogger().info("DecentHolograms 연동 완료.");
        } else {
            plugin.getLogger().warning("DecentHolograms 미설치 - 잭팟 홀로그램 비활성화.");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    private String getHologramName(String towerId) {
        return "luckytower_jackpot_" + towerId;
    }

    /**
     * 홀로그램 생성 또는 잭팟 금액 라인 갱신
     * Line 0: 타이틀  Line 1: 설명  Line 2: 금액
     */
    public void createOrUpdateHologram(String towerId, BlockPos pos, double amount) {
        if (!enabled) return;
        Location loc = getLocation(pos);
        if (loc == null) return;
        String name = getHologramName(towerId);

        try {
            Hologram existing = DHAPI.getHologram(name);
            if (existing != null) {
                // 금액 라인만 갱신
                DHAPI.setHologramLine(existing, 2, buildAmountLine(amount));
                return;
            }
            // 신규 생성
            List<String> lines = List.of(
                    "§6✦ JACKPOT ✦",
                    "§7현재 잭팟 금액",
                    buildAmountLine(amount)
            );
            DHAPI.createHologram(name, loc, false, lines);
        } catch (Exception e) {
            plugin.getLogger().warning("홀로그램 처리 실패 [" + towerId + "]: " + e.getMessage());
        }
    }

    public void removeHologram(String towerId) {
        if (!enabled) return;
        try {
            DHAPI.removeHologram(getHologramName(towerId));
        } catch (Exception ignored) {}
    }

    private String buildAmountLine(double amount) {
        String formatted = NumberFormat.getNumberInstance(Locale.KOREA).format((long) amount);
        return "§e" + formatted + " §6원";
    }

    private Location getLocation(BlockPos pos) {
        if (pos == null) return null;
        org.bukkit.block.Block block = pos.getBlock();
        if (block == null) return null;
        // 블록 중앙 상단에 홀로그램 표시
        return block.getLocation().add(0.5, 1.0, 0.5);
    }
}
