package org.thetower.tt_luckytower.manager;

import org.bukkit.configuration.file.YamlConfiguration;
import org.thetower.tt_luckytower.LuckyTowerPlugin;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

public class UserDataManager {

    private final LuckyTowerPlugin plugin;
    private final File userDataDir;

    public UserDataManager(LuckyTowerPlugin plugin) {
        this.plugin = plugin;
        this.userDataDir = new File(plugin.getDataFolder(), "userdata");
        if (!userDataDir.exists()) userDataDir.mkdirs();
    }

    private File getFile(UUID uuid) {
        return new File(userDataDir, uuid.toString() + ".yml");
    }

    private YamlConfiguration load(UUID uuid) {
        return YamlConfiguration.loadConfiguration(getFile(uuid));
    }

    private void save(UUID uuid, YamlConfiguration config) {
        try {
            config.save(getFile(uuid));
        } catch (IOException e) {
            plugin.getLogger().warning("유저 데이터 저장 실패: " + uuid + " - " + e.getMessage());
        }
    }

    public int getTickets(UUID uuid) {
        return load(uuid).getInt("tickets", 0);
    }

    public void addTickets(UUID uuid, int amount) {
        YamlConfiguration config = load(uuid);
        int current = config.getInt("tickets", 0);
        config.set("tickets", current + amount);
        save(uuid, config);
    }

    /**
     * 티켓 1장 소모. 보유 중이면 차감 후 true, 없으면 false.
     */
    public boolean useTicket(UUID uuid) {
        YamlConfiguration config = load(uuid);
        int current = config.getInt("tickets", 0);
        if (current <= 0) return false;
        config.set("tickets", current - 1);
        save(uuid, config);
        return true;
    }

    // ── 슈퍼 티켓 ──

    public int getSuperTickets(UUID uuid) {
        return load(uuid).getInt("super-tickets", 0);
    }

    public void addSuperTickets(UUID uuid, int amount) {
        YamlConfiguration config = load(uuid);
        int current = config.getInt("super-tickets", 0);
        config.set("super-tickets", current + amount);
        save(uuid, config);
    }

    /**
     * 슈퍼 티켓 1장 소모. 보유 중이면 차감 후 true, 없으면 false.
     */
    public boolean useSuperTicket(UUID uuid) {
        YamlConfiguration config = load(uuid);
        int current = config.getInt("super-tickets", 0);
        if (current <= 0) return false;
        config.set("super-tickets", current - 1);
        save(uuid, config);
        return true;
    }
}
