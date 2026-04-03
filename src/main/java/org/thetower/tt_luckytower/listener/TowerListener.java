package org.thetower.tt_luckytower.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.thetower.tt_luckytower.LuckyTowerPlugin;
import org.thetower.tt_luckytower.config.BoostItem;
import org.thetower.tt_luckytower.config.TowerConfig;
import org.thetower.tt_luckytower.game.GameSession;
import org.thetower.tt_luckytower.gui.TowerStartGui;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public class TowerListener implements Listener {

    private final LuckyTowerPlugin plugin;

    public TowerListener(LuckyTowerPlugin plugin) {
        this.plugin = plugin;
    }

    /** 상호작용 블록 우클릭 → 시작 GUI 오픈 */
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getClickedBlock() == null) return;

        Player player = event.getPlayer();
        TowerConfig tower = plugin.getTowerManager().getTowerByBlock(event.getClickedBlock());
        if (tower == null) return;

        event.setCancelled(true);

        if (plugin.getGameManager().hasSession(player.getUniqueId())) {
            player.sendMessage("§c이미 럭키타워 게임 중입니다!");
            return;
        }

        if (plugin.getGameManager().isTowerOccupied(tower.getId())) {
            player.sendMessage("§c해당 타워는 현재 다른 플레이어가 도전 중입니다!");
            return;
        }

        new TowerStartGui(plugin, tower).open(player);
    }

    /** GUI 클릭 처리 */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = event.getView().getTitle();
        if (!title.startsWith(TowerStartGui.GUI_TITLE_PREFIX)) return;

        event.setCancelled(true);

        String towerId = TowerStartGui.extractTowerId(title);
        if (towerId == null) return;
        TowerConfig tower = plugin.getTowerManager().getTower(towerId);
        if (tower == null) return;

        int slot = event.getRawSlot();
        UUID uuid = player.getUniqueId();
        List<BoostItem> boostItems = tower.getBoostItems();

        if (slot == 8) {
            // 나침반 클릭 → 다음 페이지
            int currentPage = TowerStartGui.getGuidePageFromItem(event.getCurrentItem());
            int nextPage = (currentPage + 1) % 3;
            event.getInventory().setItem(8, TowerStartGui.buildGuideItem(nextPage));

        } else if (TowerStartGui.isBoostSlot(slot, boostItems.size())) {
            // 부스트 아이콘 클릭 → 단일 선택 토글
            int boostIndex = TowerStartGui.getBoostIndexFromItem(event.getCurrentItem());
            if (boostIndex < 0 || boostIndex >= boostItems.size()) return;

            BoostItem bi = boostItems.get(boostIndex);
            if (!hasEnough(player, bi)) return; // 보유 아이템 없으면 무시

            Boolean toggleResult = TowerStartGui.toggleBoost(uuid, boostIndex);
            if (toggleResult == null) {
                // 다른 부스트가 이미 활성화 중
                player.sendMessage("§8[§6럭키타워§8] §c이미 다른 부스트가 활성화 중입니다. 동시에 사용할 수 없습니다.");
                return;
            }

            boolean nowSelected = toggleResult;
            TowerStartGui gui = new TowerStartGui(plugin, tower);

            // 부스트 아이콘 갱신
            event.getInventory().setItem(9 + boostIndex,
                    gui.makeBoostStatus(player, bi, boostIndex, nowSelected));

            // 초록 버튼 상태 갱신
            boolean anyAvailable = boostItems.stream().anyMatch(b -> hasEnough(player, b));
            event.getInventory().setItem(18,
                    gui.makeBoostStartButton(anyAvailable, TowerStartGui.hasSelection(uuid)));

        } else if (slot == 18) {
            // 부스트 도전 시작
            Set<Integer> selected = TowerStartGui.getSelectedBoosts(uuid);
            // 선택 없으면(회색/노랑 상태) 동작 안 함
            if (selected.isEmpty()) return;

            player.closeInventory();
            TowerStartGui.clearSelection(uuid);
            plugin.getGameManager().startGame(player, tower, selected);

        } else if (slot == 19) {
            // 부스트 없이 시작
            player.closeInventory();
            TowerStartGui.clearSelection(uuid);
            plugin.getGameManager().startGame(player, tower, Set.of());

        } else if (slot == 26) {
            TowerStartGui.clearSelection(uuid);
            player.closeInventory();
        }
    }

    /** 플레이어 종료 시 게임 세션 강제 종료 */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        TowerStartGui.clearSelection(player.getUniqueId());
        GameSession session = plugin.getGameManager().getSession(player.getUniqueId());
        if (session != null) {
            session.endGame(GameSession.EndReason.FORCE_STOP);
        }
    }

    private boolean hasEnough(Player player, BoostItem bi) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == bi.getMaterial()) count += item.getAmount();
        }
        return count >= bi.getAmount();
    }
}
