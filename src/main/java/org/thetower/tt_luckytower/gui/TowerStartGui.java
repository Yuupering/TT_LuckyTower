package org.thetower.tt_luckytower.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.thetower.tt_luckytower.LuckyTowerPlugin;
import org.thetower.tt_luckytower.config.BoostItem;
import org.thetower.tt_luckytower.config.FloorConfig;
import org.thetower.tt_luckytower.config.TowerConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * 럭키타워 시작 GUI (3행 27슬롯)
 *
 * [0~8]  정보 행
 * [9~17] 부스트 아이템 상태 행
 * [18]   시작 버튼 (부스트 O)
 * [19]   시작 버튼 (부스트 X)
 * [26]   취소 버튼
 */
public class TowerStartGui {

    public static final String GUI_TITLE_PREFIX = "§6럭키타워 §8| ";

    private final LuckyTowerPlugin plugin;
    private final TowerConfig tower;

    public TowerStartGui(LuckyTowerPlugin plugin, TowerConfig tower) {
        this.plugin = plugin;
        this.tower = tower;
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, GUI_TITLE_PREFIX + tower.getId());

        // ── 정보 아이템 (슬롯 4) ──
        inv.setItem(4, makeInfo(player));

        // ── 부스트 아이템 상태 (슬롯 9~) ──
        int boostSlot = 9;
        for (BoostItem bi : tower.getBoostItems()) {
            inv.setItem(boostSlot++, makeBoostStatus(player, bi));
        }

        // ── 필러 ──
        ItemStack gray = makeItem(Material.GRAY_STAINED_GLASS_PANE, "§r", List.of());
        for (int i = 0; i < 27; i++) {
            if (inv.getItem(i) == null) inv.setItem(i, gray);
        }

        // ── 시작 버튼 (부스트 포함) ──
        inv.setItem(18, makeItem(Material.LIME_STAINED_GLASS_PANE, "§a▶ 도전 시작 (부스트 적용)",
                List.of("§7보유 중인 부스트 아이템을 소모합니다.", "§7입장료: §f" + plugin.getVaultHook().format(tower.getEntryFeeVault()))));

        // ── 시작 버튼 (부스트 없음) ──
        inv.setItem(19, makeItem(Material.YELLOW_STAINED_GLASS_PANE, "§e▶ 도전 시작 (부스트 없음)",
                List.of("§7부스트 아이템 소모 없이 시작합니다.", "§7입장료: §f" + plugin.getVaultHook().format(tower.getEntryFeeVault()))));

        // ── 취소 버튼 ──
        inv.setItem(26, makeItem(Material.RED_STAINED_GLASS_PANE, "§c✕ 취소", List.of("§7도전을 취소합니다.")));

        player.openInventory(inv);
    }

    private ItemStack makeInfo(Player player) {
        ItemStack item = new ItemStack(Material.GOLD_BLOCK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§6" + tower.getId() + " 정보");
        List<String> lore = new ArrayList<>();
        lore.add("§7입장료: §f" + plugin.getVaultHook().format(tower.getEntryFeeVault()));
        lore.add("§7내 잔액: §f" + plugin.getVaultHook().format(plugin.getVaultHook().getBalance(player)));
        lore.add("§7총 층수: §f" + tower.getFloors().size() + "층");
        lore.add("§7리전: §f" + tower.getRegionId());

        // 보유 부스트 합산 미리보기
        double totalBoost = calcPlayerBoost(player);
        if (totalBoost > 0) {
            lore.add("§7───────────────");
            lore.add("§e현재 부스트: §a+" + String.format("%.1f", totalBoost) + "%");
            lore.add("§7└ 실패 확률에서 차감 → 성공 확률 증가");
            // 0층 기준 미리보기
            FloorConfig floor0 = tower.getFirstFloor();
            if (floor0 != null) {
                int[] eff = calcEffectiveForGui(floor0, totalBoost);
                lore.add("§70층 기준: §a성공 " + eff[0] + "% §c실패 " + eff[1] + "% §5리셋 " + eff[2] + "%");
            }
        }

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makeBoostStatus(Player player, BoostItem bi) {
        boolean has = hasEnough(player, bi);
        ItemStack item = new ItemStack(bi.getMaterial());
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName((has ? "§a" : "§c") + bi.getMaterial().name());
        List<String> lore = new ArrayList<>();
        lore.add("§7필요 수량: §f" + bi.getAmount() + "개");
        lore.add("§a+" + bi.getBoost() + "% §7성공 확률 증가");
        lore.add("§7└ §c실패 확률에서 차감§7 (리셋 확률 불변)");
        lore.add("§7이 게임 §f1판§7 동안만 적용 후 소멸");
        lore.add(has ? "§a✔ 보유 중 (소모 가능)" : "§c✘ 부족");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /** 플레이어가 보유한 부스트 아이템 합산 (소모하지 않음, GUI 미리보기용) */
    private double calcPlayerBoost(Player player) {
        double total = 0;
        for (BoostItem bi : tower.getBoostItems()) {
            if (hasEnough(player, bi)) total += bi.getBoost();
        }
        return total;
    }

    /** GameSession.calcEffective 와 동일 로직 (GUI 미리보기용 정적 계산) */
    private static int[] calcEffectiveForGui(FloorConfig floor, double boostPercent) {
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

    private boolean hasEnough(Player player, BoostItem bi) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == bi.getMaterial()) count += item.getAmount();
        }
        return count >= bi.getAmount();
    }

    private ItemStack makeItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /** GUI 제목으로 타워 ID 추출 */
    public static String extractTowerId(String title) {
        if (!title.startsWith(GUI_TITLE_PREFIX)) return null;
        return title.substring(GUI_TITLE_PREFIX.length());
    }
}
