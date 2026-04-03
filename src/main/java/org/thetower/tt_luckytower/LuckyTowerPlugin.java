package org.thetower.tt_luckytower;

import org.bukkit.plugin.java.JavaPlugin;
import org.thetower.tt_luckytower.command.ChartCommand;
import org.thetower.tt_luckytower.command.LuckyTowerCommand;
import org.thetower.tt_luckytower.game.GameManager;
import org.thetower.tt_luckytower.hook.DecentHologramsHook;
import org.thetower.tt_luckytower.hook.MMOItemsHook;
import org.thetower.tt_luckytower.hook.VaultHook;
import org.thetower.tt_luckytower.jackpot.JackpotManager;
import org.thetower.tt_luckytower.listener.TowerListener;
import org.thetower.tt_luckytower.manager.LightManager;
import org.thetower.tt_luckytower.manager.RewardManager;
import org.thetower.tt_luckytower.manager.TowerManager;
import org.thetower.tt_luckytower.manager.UserDataManager;

public class LuckyTowerPlugin extends JavaPlugin {

    private static LuckyTowerPlugin instance;

    private VaultHook vaultHook;
    private MMOItemsHook mmoItemsHook;
    private DecentHologramsHook dhHook;
    private TowerManager towerManager;
    private GameManager gameManager;
    private RewardManager rewardManager;
    private LightManager lightManager;
    private JackpotManager jackpotManager;
    private UserDataManager userDataManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        // ── Vault 연동 ──
        vaultHook = new VaultHook(this);
        if (!vaultHook.setup()) {
            getLogger().severe("Vault 또는 경제 플러그인을 찾을 수 없습니다! 플러그인 비활성화.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // ── MMOItems 소프트 연동 ──
        mmoItemsHook = new MMOItemsHook(this);
        mmoItemsHook.setup();

        // ── DecentHolograms 소프트 연동 ──
        dhHook = new DecentHologramsHook(this);
        dhHook.setup();

        // ── 매니저 초기화 ──
        userDataManager = new UserDataManager(this);
        lightManager = new LightManager(this);
        rewardManager = new RewardManager(this);
        towerManager = new TowerManager(this);
        towerManager.loadTowers();
        gameManager = new GameManager(this);

        // ── 잭팟 매니저 초기화 ──
        jackpotManager = new JackpotManager(this);
        jackpotManager.loadData();

        // ── 이벤트 등록 ──
        getServer().getPluginManager().registerEvents(new TowerListener(this), this);

        // ── 커맨드 등록 ──
        LuckyTowerCommand cmd = new LuckyTowerCommand(this);
        getCommand("luckytower").setExecutor(cmd);
        getCommand("luckytower").setTabCompleter(cmd);

        ChartCommand chartCmd = new ChartCommand(this);
        getCommand("확률표").setExecutor(chartCmd);
        getCommand("확률표").setTabCompleter(chartCmd);

        getLogger().info("TT_LuckyTower v" + getDescription().getVersion() + " 활성화 완료!");
    }

    @Override
    public void onDisable() {
        if (gameManager != null) {
            gameManager.endAllSessions();
        }
        if (jackpotManager != null) {
            jackpotManager.removeAllHolograms();
        }
        getLogger().info("TT_LuckyTower 비활성화.");
    }

    public static LuckyTowerPlugin getInstance() { return instance; }
    public VaultHook getVaultHook() { return vaultHook; }
    public MMOItemsHook getMmoItemsHook() { return mmoItemsHook; }
    public DecentHologramsHook getDhHook() { return dhHook; }
    public TowerManager getTowerManager() { return towerManager; }
    public GameManager getGameManager() { return gameManager; }
    public RewardManager getRewardManager() { return rewardManager; }
    public LightManager getLightManager() { return lightManager; }
    public JackpotManager getJackpotManager() { return jackpotManager; }
    public UserDataManager getUserDataManager() { return userDataManager; }
}
