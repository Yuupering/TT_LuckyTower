package org.thetower.tt_luckytower.hook;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.thetower.tt_luckytower.LuckyTowerPlugin;

public class VaultHook {

    private final LuckyTowerPlugin plugin;
    private Economy economy;

    public VaultHook(LuckyTowerPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean setup() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) return false;
        RegisteredServiceProvider<Economy> rsp = plugin.getServer()
                .getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        economy = rsp.getProvider();
        return economy != null;
    }

    public boolean has(Player player, double amount) {
        return economy.has(player, amount);
    }

    public boolean withdraw(Player player, double amount) {
        return economy.withdrawPlayer(player, amount).transactionSuccess();
    }

    public boolean deposit(Player player, double amount) {
        return economy.depositPlayer(player, amount).transactionSuccess();
    }

    public double getBalance(Player player) {
        return economy.getBalance(player);
    }

    public String format(double amount) {
        return economy.format(amount);
    }
}
