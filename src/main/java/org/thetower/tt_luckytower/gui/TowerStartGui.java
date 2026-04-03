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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 럭키타워 시작 GUI (3행 27슬롯)
 *
 * [4] 타워 정보   [8] 나침반(클릭 시 페이지 순환)
 * [9~] 부스트 아이템 상태 (클릭으로 선택/해제)
 * [18] 시작(부스트O)  [19] 시작(부스트X)  [26] 취소
 */
public class TowerStartGui {

    public static final String GUI_TITLE_PREFIX = "§6럭키타워 §8| ";
    private static final int GUIDE_MAX_PAGES = 3;

    /** UUID → 선택된 부스트 인덱스 (하나만 허용, null = 미선택) */
    private static final Map<UUID, Integer> selectedBoost = new HashMap<>();

    private final LuckyTowerPlugin plugin;
    private final TowerConfig tower;

    public TowerStartGui(LuckyTowerPlugin plugin, TowerConfig tower) {
        this.plugin = plugin;
        this.tower = tower;
    }

    public void open(Player player) {
        // GUI 열 때 선택 초기화
        selectedBoost.remove(player.getUniqueId());

        Inventory inv = Bukkit.createInventory(null, 27, GUI_TITLE_PREFIX + tower.getId());

        inv.setItem(4, makeInfo(player));
        inv.setItem(8, buildGuideItem(0));

        List<BoostItem> boostItems = tower.getBoostItems();
        for (int i = 0; i < boostItems.size(); i++) {
            inv.setItem(9 + i, makeBoostStatus(player, boostItems.get(i), i, false));
        }

        ItemStack gray = makeItem(Material.GRAY_STAINED_GLASS_PANE, "§r");
        for (int i = 0; i < 27; i++) {
            if (inv.getItem(i) == null) inv.setItem(i, gray);
        }

        // 부스트 시작 버튼: 보유 가능한 부스트 아이템이 하나도 없으면 비활성화
        boolean anyBoostAvailable = boostItems.stream().anyMatch(bi -> hasEnough(player, bi));
        inv.setItem(18, makeBoostStartButton(anyBoostAvailable, false));

        inv.setItem(19, makeItem(Material.YELLOW_STAINED_GLASS_PANE, "§e▶ 도전 시작 (부스트 없음)",
                "§7부스트 아이템 소모 없이 시작합니다.",
                "§7입장료: §f" + plugin.getVaultHook().format(tower.getEntryFeeVault())));

        inv.setItem(26, makeItem(Material.RED_STAINED_GLASS_PANE, "§c✕ 취소",
                "§7도전을 취소합니다."));

        player.openInventory(inv);
    }

    // ── 부스트 시작 버튼 빌더 ──

    public ItemStack makeBoostStartButton(boolean boostAvailable, boolean hasSelection) {
        if (!boostAvailable) {
            // 부스트 아이템 미보유 → 회색으로 비활성화
            return makeItem(Material.GRAY_STAINED_GLASS_PANE,
                    "§7▶ 도전 시작 (부스트 적용)",
                    "§c보유 중인 부스트 아이템이 없습니다.");
        }
        if (!hasSelection) {
            // 보유는 있지만 선택 전 → 아이템 클릭 유도
            return makeItem(Material.YELLOW_STAINED_GLASS_PANE,
                    "§e▶ 도전 시작 (부스트 적용)",
                    "§7아래 부스트 아이콘을 클릭하여 선택하세요.",
                    "§7선택 후 이 버튼이 §a초록색§7으로 바뀝니다.");
        }
        // 선택된 부스트 있음 → 초록 활성
        return makeItem(Material.LIME_STAINED_GLASS_PANE,
                "§a▶ 도전 시작 (부스트 적용)",
                "§7선택한 부스트 아이템을 소모하고 시작합니다.",
                "§7입장료: §f" + plugin.getVaultHook().format(tower.getEntryFeeVault()));
    }

    // ── 부스트 아이템 아이콘 ──

    public ItemStack makeBoostStatus(Player player, BoostItem bi, int index, boolean selected) {
        boolean has = hasEnough(player, bi);
        ItemStack item = new ItemStack(bi.getMaterial());
        ItemMeta meta = item.getItemMeta();

        String prefix = selected ? "§b[✔ 선택됨] " : (has ? "§a" : "§c");
        meta.setDisplayName(prefix + bi.getMaterial().name());

        List<String> lore = new ArrayList<>();
        lore.add("§7필요 수량: §f" + bi.getAmount() + "개");
        lore.add("§a+" + bi.getBoost() + "% §7성공 확률 증가");
        lore.add("§7└ §c실패 확률에서 차감§7 (리셋 확률 불변)");
        lore.add("§7이 게임 §f1판§7 동안만 적용 후 소멸");
        if (!has) {
            lore.add("§c✘ 아이템 부족");
        } else if (selected) {
            lore.add("§b✔ 선택됨 §7(다시 클릭하면 해제)");
        } else {
            lore.add("§a클릭하여 선택");
        }
        // 인덱스 인코딩 (리스너에서 파싱용)
        lore.add("§0idx:" + index);

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    // ── 나침반 안내 아이템 (페이지 순환) ──

    public static ItemStack buildGuideItem(int page) {
        String nextLabel = (page + 1 < GUIDE_MAX_PAGES)
                ? "§7클릭하여 다음 페이지 »"
                : "§7클릭하여 처음으로 »";
        String pageLabel = "§8[" + (page + 1) + "/" + GUIDE_MAX_PAGES + "]  " + nextLabel;

        return switch (page) {
            case 0 -> makeItem(Material.COMPASS,
                    "§e럭키타워  §8[게임 방식]",
                    "§8────────────────────",
                    "§f버튼을 클릭하면 자동으로 층을 오릅니다.",
                    "§f각 층마다 §a성공 §c실패 §5리셋 §f중 하나가 판정됩니다.",
                    "§f층을 오를수록 보상이 누적되며,",
                    "§f마지막 층 클리어 시 잭팟을 수령합니다.",
                    "§8────────────────────",
                    pageLabel);
            case 1 -> makeItem(Material.COMPASS,
                    "§e럭키타워  §8[판정 결과]",
                    "§8────────────────────",
                    "§a성공  §f다음 층으로 진행, 보상 누적",
                    "§c실패  §f모든 누적 보상 즉시 초기화",
                    "§5리셋  §f모든 누적 보상 초기화",
                    "       §f마지막 체크포인트 위치로 복귀",
                    "§8────────────────────",
                    "§e체크포인트 §8(리셋 층계)",
                    "§f★ 표시 층 도달 시 체크포인트 갱신.",
                    "§f이후 리셋 이벤트 발생 시 해당 층으로 복귀.",
                    "§8────────────────────",
                    pageLabel);
            default -> makeItem(Material.COMPASS,
                    "§e럭키타워  §8[잭팟 & 부스트]",
                    "§8────────────────────",
                    "§e잭팟",
                    "§f입장료의 일부가 잭팟 풀에 자동 적립됩니다.",
                    "§f리셋 층계 도달 실패 시 설정 확률로 잭팟 발동.",
                    "§f마지막 층 클리어 시 §e100% §f확률로 수령.",
                    "§8────────────────────",
                    "§a부스트 아이템",
                    "§f특정 아이템 소모 → 성공 확률 증가.",
                    "§c실패 확률에서 차감§f되며 §f1판§f 동안만 적용.",
                    "§8────────────────────",
                    pageLabel);
        };
    }

    /** 나침반 아이템의 lore 마지막 줄에서 현재 페이지 번호 추출 */
    public static int getGuidePageFromItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        List<String> lore = item.getItemMeta().getLore();
        if (lore == null || lore.isEmpty()) return 0;
        // 마지막 줄 형식: "§8[X/Y]  §7..."
        String last = lore.get(lore.size() - 1);
        try {
            int start = last.indexOf('[') + 1;
            int end   = last.indexOf('/');
            if (start > 0 && end > start) {
                return Integer.parseInt(last.substring(start, end).replaceAll("§.", "").trim()) - 1;
            }
        } catch (NumberFormatException ignored) {}
        return 0;
    }

    /** 부스트 아이콘의 lore에서 인덱스 추출 */
    public static int getBoostIndexFromItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return -1;
        List<String> lore = item.getItemMeta().getLore();
        if (lore == null) return -1;
        for (String line : lore) {
            if (line.startsWith("§0idx:")) {
                try { return Integer.parseInt(line.substring(6)); }
                catch (NumberFormatException ignored) {}
            }
        }
        return -1;
    }

    // ── 선택 상태 관리 ──

    /** 현재 선택된 부스트 인덱스를 Set으로 반환 (startGame 호환용) */
    public static Set<Integer> getSelectedBoosts(UUID uuid) {
        Integer idx = selectedBoost.get(uuid);
        return idx != null ? Set.of(idx) : Collections.emptySet();
    }

    public static void clearSelection(UUID uuid) {
        selectedBoost.remove(uuid);
    }

    public static boolean hasSelection(UUID uuid) {
        return selectedBoost.containsKey(uuid);
    }

    /**
     * 부스트 아이콘 클릭 시 단일 선택 토글.
     *
     * @return {@code true}=선택됨, {@code false}=해제됨, {@code null}=다른 부스트 활성화 중(차단)
     */
    public static Boolean toggleBoost(UUID uuid, int index) {
        Integer current = selectedBoost.get(uuid);
        if (current != null && current != index) {
            return null; // 다른 부스트가 이미 선택된 상태 → 차단
        }
        if (current != null) {
            selectedBoost.remove(uuid); // 같은 것 → 해제
            return false;
        }
        selectedBoost.put(uuid, index); // 새로 선택
        return true;
    }

    // ── 타워 정보 아이템 ──

    private ItemStack makeInfo(Player player) {
        ItemStack item = new ItemStack(Material.GOLD_BLOCK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§6" + tower.getId() + " 정보");
        List<String> lore = new ArrayList<>();
        lore.add("§7입장료: §f" + plugin.getVaultHook().format(tower.getEntryFeeVault()));
        lore.add("§7내 잔액: §f" + plugin.getVaultHook().format(plugin.getVaultHook().getBalance(player)));
        lore.add("§7총 층수: §f" + tower.getFloors().size() + "층");
        lore.add("§7리전: §f" + tower.getRegionId());

        double totalBoost = calcPlayerBoost(player);
        if (totalBoost > 0) {
            lore.add("§7───────────────");
            lore.add("§e현재 부스트: §a+" + String.format("%.1f", totalBoost) + "%");
            lore.add("§7└ 실패 확률에서 차감 → 성공 확률 증가");
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

    private static ItemStack makeItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(Arrays.asList(lore));
        item.setItemMeta(meta);
        return item;
    }

    private double calcPlayerBoost(Player player) {
        double total = 0;
        for (BoostItem bi : tower.getBoostItems()) {
            if (hasEnough(player, bi)) total += bi.getBoost();
        }
        return total;
    }

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

    public static String extractTowerId(String title) {
        if (!title.startsWith(GUI_TITLE_PREFIX)) return null;
        return title.substring(GUI_TITLE_PREFIX.length());
    }

    /** 리스너에서 부스트 아이콘 슬롯 범위 판단용: 9 ~ 9+boostItems.size()-1 */
    public static boolean isBoostSlot(int rawSlot, int boostCount) {
        return rawSlot >= 9 && rawSlot < 9 + boostCount;
    }
}
