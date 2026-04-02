package org.thetower.tt_luckytower.manager;

import org.bukkit.configuration.file.YamlConfiguration;
import org.thetower.tt_luckytower.LuckyTowerPlugin;
import org.thetower.tt_luckytower.config.BlockPos;
import org.thetower.tt_luckytower.config.TowerConfig;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 타워 설정 관리자.
 * 각 타워의 설정은 plugins/TT_LuckyTower/towers/<타워ID>.yml 파일에 저장됩니다.
 * towers/ 폴더가 비어있으면 config.yml 의 towers: 섹션에서 자동 마이그레이션합니다.
 */
public class TowerManager {

    private final LuckyTowerPlugin plugin;
    private final Map<String, TowerConfig> towers = new HashMap<>();
    private File towersDir;

    public TowerManager(LuckyTowerPlugin plugin) {
        this.plugin = plugin;
    }

    // ──────────────────── 로드 ────────────────────

    public void loadTowers() {
        towersDir = new File(plugin.getDataFolder(), "towers");
        if (!towersDir.exists()) towersDir.mkdirs();

        towers.clear();

        File[] yamlFiles = towersDir.listFiles((d, name) -> name.endsWith(".yml"));

        // towers/ 폴더가 비어있으면 config.yml 에서 마이그레이션
        if (yamlFiles == null || yamlFiles.length == 0) {
            migrateFromConfig();
            yamlFiles = towersDir.listFiles((d, name) -> name.endsWith(".yml"));
        }

        if (yamlFiles != null) {
            for (File file : yamlFiles) {
                loadTowerFromFile(file);
            }
        }

        plugin.getLogger().info("총 " + towers.size() + "개의 타워가 로드되었습니다.");
    }

    private void loadTowerFromFile(File file) {
        String id = file.getName().replace(".yml", "");
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        TowerConfig config = TowerConfig.fromConfig(id, cfg);
        towers.put(id, config);
        plugin.getLogger().info("타워 로드: " + id
                + " (그룹: " + config.getGroupId()
                + ", " + config.getFloors().size() + "층)");
    }

    /** config.yml 의 towers: 섹션을 개별 yml 파일로 마이그레이션 */
    private void migrateFromConfig() {
        var towersSection = plugin.getConfig().getConfigurationSection("towers");
        if (towersSection == null) return;

        for (String id : towersSection.getKeys(false)) {
            var towerSection = towersSection.getConfigurationSection(id);
            if (towerSection == null) continue;

            File towerFile = new File(towersDir, id + ".yml");
            YamlConfiguration newCfg = new YamlConfiguration();

            // 모든 키/값 복사 (중첩 포함)
            for (String key : towerSection.getKeys(true)) {
                newCfg.set(key, towerSection.get(key));
            }

            try {
                newCfg.save(towerFile);
                plugin.getLogger().info("타워 마이그레이션: " + id + " → towers/" + id + ".yml");
            } catch (IOException e) {
                plugin.getLogger().warning("타워 마이그레이션 실패 (" + id + "): " + e.getMessage());
            }
        }
    }

    // ──────────────────── 조회 ────────────────────

    /** 상호작용 블록 좌표로 타워 조회 */
    public TowerConfig getTowerByBlock(org.bukkit.block.Block block) {
        for (TowerConfig tower : towers.values()) {
            BlockPos pos = tower.getInteractionBlock();
            if (pos == null) continue;
            org.bukkit.block.Block towerBlock = pos.getBlock();
            if (towerBlock != null && towerBlock.equals(block)) return tower;
        }
        return null;
    }

    public TowerConfig getTower(String id) {
        return towers.get(id);
    }

    public Collection<TowerConfig> getAllTowers() {
        return towers.values();
    }

    /** 특정 WorldGuard 리전 ID에 연결된 타워 목록 반환 (한 리전에 여러 타워 가능) */
    public List<TowerConfig> getTowersByRegion(String regionId) {
        List<TowerConfig> result = new ArrayList<>();
        for (TowerConfig tower : towers.values()) {
            if (tower.getRegionId().equalsIgnoreCase(regionId)) result.add(tower);
        }
        return result;
    }

    // ──────────────────── 런타임 수정 ────────────────────

    /**
     * 특정 층의 램프 좌표를 타워 yml에 추가.
     * /lt setlight 커맨드에서 사용.
     */
    public void addLightToFloor(String towerId, int floor, BlockPos pos) {
        File file = getTowerFile(towerId);
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        String path = "floors." + floor + ".lights";
        java.util.List<String> lights = cfg.getStringList(path);
        lights.add(pos.toString());
        cfg.set(path, lights);
        saveTowerFile(towerId, cfg);
        loadTowers();
    }

    /** 홀로그램 위치를 타워 yml에 저장. */
    public void setHologramPos(String towerId, BlockPos pos) {
        File file = getTowerFile(towerId);
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        cfg.set("jackpot.hologram.world", pos.getWorldName());
        cfg.set("jackpot.hologram.x", pos.getX());
        cfg.set("jackpot.hologram.y", pos.getY());
        cfg.set("jackpot.hologram.z", pos.getZ());
        saveTowerFile(towerId, cfg);
        loadTowers();
    }

    /** 상호작용 블록 위치를 타워 yml에 저장. /lt setblock 커맨드에서 사용. */
    public void setInteractionBlock(String towerId, BlockPos pos) {
        File file = getTowerFile(towerId);
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        cfg.set("interaction-block.world", pos.getWorldName());
        cfg.set("interaction-block.x", pos.getX());
        cfg.set("interaction-block.y", pos.getY());
        cfg.set("interaction-block.z", pos.getZ());
        saveTowerFile(towerId, cfg);
        loadTowers();
    }

    // ──────────────────── 타워 신규 생성 (/lt start) ────────────────────

    /**
     * /lt start <그룹명> <타워명> <리전명> <층 높이> 에서 호출.
     * 타워 yml을 자동 생성하고, 레드스톤 램프 위치를 floors 섹션에 등록합니다.
     * (실제 블록 설치는 커맨드 핸들러에서 수행)
     *
     * @param groupId       잭팟 공유 그룹 ID
     * @param towerId       새 타워 ID (yml 파일명)
     * @param regionId      WorldGuard 리전 ID
     * @param interaction   상호작용 블록 위치
     * @param floorHeight   층 간격 (블록 수)
     * @param worldName     월드 이름
     * @return true = 생성 성공 / false = 이미 존재하는 타워 ID
     */
    /**
     * /lt start 로 새 타워를 생성합니다.
     *
     * @param interaction  버튼 블록 위치 (상호작용 블록으로 등록)
     * @param lampBaseX    0층 레드스톤 램프 X 좌표 (버튼 뒤 한 칸)
     * @param lampBaseY    0층 레드스톤 램프 Y 좌표
     * @param lampBaseZ    0층 레드스톤 램프 Z 좌표
     * @param floorCount   총 층 수 (= 설치할 레드스톤 램프 개수), floors 0 ~ floorCount-1 생성
     * @param worldName    월드 이름
     */
    public boolean createTower(String groupId, String towerId, String regionId,
                               BlockPos interaction,
                               int lampBaseX, int lampBaseY, int lampBaseZ,
                               int floorCount, String worldName) {
        if (towers.containsKey(towerId)) return false;

        File file = new File(towersDir, towerId + ".yml");
        if (file.exists()) return false;

        YamlConfiguration cfg = new YamlConfiguration();

        cfg.set("group", groupId);
        cfg.set("region", regionId);
        cfg.set("entry-fee-vault", 5000.0);

        cfg.set("interaction-block.world", interaction.getWorldName());
        cfg.set("interaction-block.x", interaction.getX());
        cfg.set("interaction-block.y", interaction.getY());
        cfg.set("interaction-block.z", interaction.getZ());

        // 부스트 아이템 기본값
        cfg.set("boost-items", List.of(
                Map.of("material", "IRON_BLOCK", "amount", 64, "boost-percent", 5.0),
                Map.of("material", "QUARTZ_BLOCK", "amount", 32, "boost-percent", 3.0)
        ));

        // floors 0 ~ floorCount-1 생성 (확률은 층 수에 맞게 자동 스케일)
        int[][] chances = generateFloorChances(floorCount);

        for (int i = 0; i < floorCount; i++) {
            String path = "floors." + i;
            cfg.set(path + ".success", chances[i][0]);
            cfg.set(path + ".fail",    chances[i][1]);
            cfg.set(path + ".reset",   chances[i][2]);

            // 리셋 층: 전체 층수의 1/3, 2/3 지점 (0층 제외)
            boolean isReset = (i > 0) && floorCount > 2
                    && (i == floorCount / 3 || i == (floorCount * 2) / 3);
            if (isReset) {
                cfg.set(path + ".reset-floor", true);
                // 2/3 지점 리셋 = 더 높은 잭팟 확률/비율
                boolean isLaterReset = (i == (floorCount * 2) / 3);
                cfg.set(path + ".jackpot-chance",  isLaterReset ? 5.0 : 3.0);
                cfg.set(path + ".jackpot-payout",  isLaterReset ? 100.0 : 50.0);
            }

            // 레드스톤 램프 위치 — 1블록 간격으로 수직 배치
            int lampY = lampBaseY + i;
            String lampPos = worldName + "," + lampBaseX + "," + lampY + "," + lampBaseZ;
            cfg.set(path + ".lights", List.of(lampPos));
        }

        // 잭팟 기본 설정
        cfg.set("jackpot.enabled", true);
        cfg.set("jackpot.initial-amount", 50000.0);
        cfg.set("jackpot.contribution-percent", 10.0);

        saveTowerFile(towerId, cfg);
        loadTowers();
        return true;
    }

    /**
     * N개의 층에 대한 확률 자동 생성.
     * 0층(쉬움): 성공 80% / 실패 15% / 리셋 5%
     * 마지막 층(어려움): 성공 45% / 실패 40% / 리셋 15%
     * 그 사이는 선형 보간.
     */
    private static int[][] generateFloorChances(int floorCount) {
        int[][] result = new int[floorCount][3];
        for (int i = 0; i < floorCount; i++) {
            double t = floorCount <= 1 ? 0.0 : (double) i / (floorCount - 1);
            int success = (int) Math.round(80 - t * 35); // 80 → 45
            int fail    = (int) Math.round(15 + t * 25); // 15 → 40
            int reset   = 100 - success - fail;           // 5 → 15
            if (reset < 0) { fail += reset; reset = 0; }
            result[i] = new int[]{success, fail, reset};
        }
        return result;
    }

    // ──────────────────── 파일 유틸 ────────────────────

    private File getTowerFile(String towerId) {
        return new File(towersDir, towerId + ".yml");
    }

    private void saveTowerFile(String towerId, YamlConfiguration cfg) {
        try {
            cfg.save(getTowerFile(towerId));
        } catch (IOException e) {
            plugin.getLogger().warning("타워 파일 저장 실패 (" + towerId + "): " + e.getMessage());
        }
    }
}
