package org.thetower.tt_luckytower.game;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.thetower.tt_luckytower.LuckyTowerPlugin;
import org.thetower.tt_luckytower.config.BoostItem;
import org.thetower.tt_luckytower.config.TowerConfig;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GameManager {

    private final LuckyTowerPlugin plugin;
    private final Map<UUID, GameSession> sessions = new HashMap<>();

    public GameManager(LuckyTowerPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 게임 시작 처리
     * - 입장료 차감
     * - 부스트 아이템 소모 (플레이어가 보유한 경우)
     * - 세션 생성 및 시작
     */
    public boolean startGame(Player player, TowerConfig tower, boolean useBoost) {
        if (sessions.containsKey(player.getUniqueId())) {
            player.sendMessage("§c이미 럭키타워 게임 중입니다!");
            return false;
        }

        // 입장료 확인
        if (!plugin.getVaultHook().has(player, tower.getEntryFeeVault())) {
            player.sendMessage("§c잔액 부족! 입장료: §f"
                    + plugin.getVaultHook().format(tower.getEntryFeeVault()));
            return false;
        }

        // 부스트 아이템 확인 및 소모
        double boostPercent = 0.0;
        if (useBoost) {
            for (BoostItem boostItem : tower.getBoostItems()) {
                if (hasEnoughItems(player, boostItem)) {
                    removeItems(player, boostItem);
                    boostPercent += boostItem.getBoost();
                    player.sendMessage("§a[럭키타워] 부스트 아이템 적용: §f"
                            + boostItem.getMaterial().name() + " x" + boostItem.getAmount()
                            + " §7(§a+" + boostItem.getBoost() + "%§7)");
                }
            }
        }

        // 입장료 차감
        plugin.getVaultHook().withdraw(player, tower.getEntryFeeVault());
        player.sendMessage("§7입장료 차감: §f" + plugin.getVaultHook().format(tower.getEntryFeeVault()));

        // 잭팟 풀 적립 (입장료의 일부)
        plugin.getJackpotManager().contribute(tower.getId(), tower.getEntryFeeVault());

        // 세션 생성 및 시작
        GameSession session = new GameSession(plugin, player.getUniqueId(), tower, boostPercent);
        sessions.put(player.getUniqueId(), session);
        session.start();
        return true;
    }

    private boolean hasEnoughItems(Player player, BoostItem boostItem) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == boostItem.getMaterial()) {
                count += item.getAmount();
            }
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

    public void removeSession(UUID playerId) {
        sessions.remove(playerId);
    }

    public GameSession getSession(UUID playerId) {
        return sessions.get(playerId);
    }

    public boolean hasSession(UUID playerId) {
        return sessions.containsKey(playerId);
    }

    public Collection<GameSession> getAllSessions() {
        return sessions.values();
    }

    /** 플러그인 종료 시 모든 세션 강제 종료 */
    public void endAllSessions() {
        for (GameSession session : sessions.values()) {
            session.endGame(GameSession.EndReason.FORCE_STOP);
        }
        sessions.clear();
    }

    /** 관리자 강제 종료 */
    public boolean forceStop(UUID playerId) {
        GameSession session = sessions.get(playerId);
        if (session == null) return false;
        session.endGame(GameSession.EndReason.FORCE_STOP);
        return true;
    }
}
