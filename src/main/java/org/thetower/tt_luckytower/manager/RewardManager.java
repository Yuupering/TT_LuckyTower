package org.thetower.tt_luckytower.manager;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.thetower.tt_luckytower.LuckyTowerPlugin;
import org.thetower.tt_luckytower.config.Reward;

import java.util.List;

public class RewardManager {

    private final LuckyTowerPlugin plugin;

    public RewardManager(LuckyTowerPlugin plugin) {
        this.plugin = plugin;
    }

    /** 보상 목록을 플레이어에게 지급 */
    public void giveRewards(Player player, List<Reward> rewards) {
        for (Reward reward : rewards) {
            giveReward(player, reward);
        }
    }

    public void giveReward(Player player, Reward reward) {
        switch (reward.getType()) {
            case VAULT -> giveVault(player, reward.getVaultAmount());
            case ITEM -> giveItem(player, new ItemStack(reward.getMaterial(), reward.getItemAmount()));
            case MMOITEM -> giveMmoItem(player, reward.getMmoItemType(), reward.getMmoItemId());
            case COMMAND -> executeCommand(player, reward.getCommand());
            case BUFF -> giveBuff(player, reward);
        }
    }

    private void giveVault(Player player, double amount) {
        plugin.getVaultHook().deposit(player, amount);
        player.sendMessage("§6[럭키타워] §e보상: §f" + plugin.getVaultHook().format(amount));
    }

    private void giveItem(Player player, ItemStack item) {
        java.util.HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        // 인벤토리가 꽉 차면 발 밑에 드롭
        for (ItemStack drop : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), drop);
        }
        player.sendMessage("§6[럭키타워] §e보상: §f" + item.getAmount() + "x " + item.getType().name());
    }

    private void giveMmoItem(Player player, String typeId, String itemId) {
        if (!plugin.getMmoItemsHook().isEnabled()) {
            plugin.getLogger().warning("MMOItems 미설치 - 보상 지급 불가: " + typeId + "/" + itemId);
            return;
        }
        ItemStack item = plugin.getMmoItemsHook().getItem(typeId, itemId);
        if (item == null) {
            plugin.getLogger().warning("MMOItem 없음: " + typeId + "/" + itemId);
            return;
        }
        giveItem(player, item);
    }

    private void executeCommand(Player player, String command) {
        String parsed = command.replace("{player}", player.getName());
        plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), parsed);
    }

    private void giveBuff(Player player, Reward reward) {
        player.addPotionEffect(new PotionEffect(
                reward.getBuffType(),
                reward.getBuffDurationTicks(),
                reward.getBuffAmplifier(),
                false, true, true
        ));
        player.sendMessage("§6[럭키타워] §e버프 획득: §f" + reward.getBuffType().getName());
    }
}
