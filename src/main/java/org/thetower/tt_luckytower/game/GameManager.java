package org.thetower.tt_luckytower.game;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.thetower.tt_luckytower.LuckyTowerPlugin;
import org.thetower.tt_luckytower.config.BoostItem;
import org.thetower.tt_luckytower.config.TowerConfig;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class GameManager {

    private final LuckyTowerPlugin plugin;
    private final Map<UUID, GameSession> sessions = new HashMap<>();

    public GameManager(LuckyTowerPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isTowerOccupied(String towerId) {
        for (GameSession session : sessions.values()) {
            if (session.isActive() && session.getTower().getId().equals(towerId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 게임 시작.
     * @param selectedBoostIndices tower.getBoostItems() 인덱스 중 소모할 항목. 빈 Set = 부스트 없음.
     */
    public boolean startGame(Player player, TowerConfig tower, Set<Integer> selectedBoostIndices) {
        if (sessions.containsKey(player.getUniqueId())) {
            player.sendMessage("§c이미 럭키타워 게임 중입니다!");
            return false;
        }

        if (isTowerOccupied(tower.getId())) {
            player.sendMessage("§c해당 타워는 현재 다른 플레이어가 도전 중입니다!");
            return false;
        }

        // 티켓 보유 시 무료 입장
        boolean usedTicket = plugin.getUserDataManager().useTicket(player.getUniqueId());

        if (!usedTicket) {
            if (!plugin.getVaultHook().has(player, tower.getEntryFeeVault())) {
                player.sendMessage("§c잔액 부족! 입장료: §f"
                        + plugin.getVaultHook().format(tower.getEntryFeeVault()));
                return false;
            }
        }

        // 선택된 부스트 아이템 소모
        double boostPercent = 0.0;
        List<BoostItem> boostItems = tower.getBoostItems();
        for (int idx : selectedBoostIndices) {
            if (idx < 0 || idx >= boostItems.size()) continue;
            BoostItem bi = boostItems.get(idx);
            if (hasEnoughItems(player, bi)) {
                removeItems(player, bi);
                boostPercent += bi.getBoost();
                player.sendMessage("§a[럭키타워] 부스트 적용: §f"
                        + bi.getMaterial().name() + " x" + bi.getAmount()
                        + " §7(§a+" + bi.getBoost() + "%§7)");
            }
        }

        if (usedTicket) {
            int remaining = plugin.getUserDataManager().getTickets(player.getUniqueId());
            player.sendMessage("§a[럭키타워] 티켓 사용! 무료 입장 §7(남은 티켓: §f" + remaining + "장§7)");
        } else {
            plugin.getVaultHook().withdraw(player, tower.getEntryFeeVault());
            player.sendMessage("§7입장료 차감: §f" + plugin.getVaultHook().format(tower.getEntryFeeVault()));
            plugin.getJackpotManager().contribute(tower.getId(), tower.getEntryFeeVault());
        }

        GameSession session = new GameSession(plugin, player.getUniqueId(), tower, boostPercent);
        sessions.put(player.getUniqueId(), session);
        session.start();
        return true;
    }

    private boolean hasEnoughItems(Player player, BoostItem boostItem) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == boostItem.getMaterial())
                count += item.getAmount();
        }
        return count >= boostItem.getAmount();
    }

    private void removeItems(Player player, BoostItem boostItem) {
        int remaining = boostItem.getAmount();
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType() != boostItem.getMaterial()) continue;
            if (item.getAmount() <= remaining) {
                remaining -= item.getAmount();
                player.getInventory().setItem(i, null);
            } else {
                item.setAmount(item.getAmount() - remaining);
                remaining = 0;
            }
        }
    }

    public void removeSession(UUID playerId) { sessions.remove(playerId); }
    public GameSession getSession(UUID playerId) { return sessions.get(playerId); }
    public boolean hasSession(UUID playerId) { return sessions.containsKey(playerId); }
    public Collection<GameSession> getAllSessions() { return sessions.values(); }

    public void endAllSessions() {
        for (GameSession session : sessions.values()) {
            session.endGame(GameSession.EndReason.FORCE_STOP);
        }
        sessions.clear();
    }

    public boolean forceStop(UUID playerId) {
        GameSession session = sessions.get(playerId);
        if (session == null) return false;
        session.endGame(GameSession.EndReason.FORCE_STOP);
        return true;
    }
}
