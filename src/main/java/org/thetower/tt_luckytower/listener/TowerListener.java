package org.thetower.tt_luckytower.listener;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.thetower.tt_luckytower.LuckyTowerPlugin;
import org.thetower.tt_luckytower.config.TowerConfig;
import org.thetower.tt_luckytower.game.GameSession;
import org.thetower.tt_luckytower.gui.TowerStartGui;

public class TowerListener implements Listener {

    private final LuckyTowerPlugin plugin;

    public TowerListener(LuckyTowerPlugin plugin) {
        this.plugin = plugin;
    }

    /** 상호작용 블록 우클릭 → GUI 오픈 */
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

        if (slot == 18) {
            // 부스트 적용 시작
            player.closeInventory();
            plugin.getGameManager().startGame(player, tower, true);
        } else if (slot == 19) {
            // 부스트 없이 시작
            player.closeInventory();
            plugin.getGameManager().startGame(player, tower, false);
        } else if (slot == 26) {
            // 취소
            player.closeInventory();
        }
    }

    /** 플레이어 종료 시 게임 세션 강제 종료 */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        GameSession session = plugin.getGameManager().getSession(player.getUniqueId());
        if (session != null) {
            session.endGame(GameSession.EndReason.FORCE_STOP);
        }
    }
}
