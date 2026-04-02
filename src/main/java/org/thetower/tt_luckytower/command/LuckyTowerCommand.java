package org.thetower.tt_luckytower.command;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.FaceAttachable;
import org.bukkit.block.data.type.Switch;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.thetower.tt_luckytower.LuckyTowerPlugin;
import org.thetower.tt_luckytower.config.BlockPos;
import org.thetower.tt_luckytower.config.TowerConfig;
import org.thetower.tt_luckytower.game.GameSession;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * /luckytower (alias: /lt)
 *
 * /lt reload                                     - 설정 리로드
 * /lt info                                       - 진행 중인 세션 목록
 * /lt stop <플레이어>                             - 강제 종료
 * /lt start <그룹명> <타워명> <리전명> <층높이>   - 새 타워 자동 생성 + 레드스톤 램프 설치
 * /lt setblock <타워ID>                          - 상호작용 블록 설정 (현재 위치의 대상 블록)
 * /lt setlight <타워ID> <층>                     - 현재 위치 블록을 해당 층 램프로 등록
 */
public class LuckyTowerCommand implements CommandExecutor, TabCompleter {

    private final LuckyTowerPlugin plugin;

    public LuckyTowerCommand(LuckyTowerPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("tt.luckytower.admin")) {
            sender.sendMessage("§c권한이 없습니다.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                plugin.reloadConfig();
                plugin.getTowerManager().loadTowers();
                plugin.getJackpotManager().loadData();
                sender.sendMessage("§a[럭키타워] 설정 리로드 완료.");
            }
            case "info" -> {
                var sessions = plugin.getGameManager().getAllSessions();
                if (sessions.isEmpty()) {
                    sender.sendMessage("§7[럭키타워] 진행 중인 세션 없음.");
                } else {
                    sender.sendMessage("§6[럭키타워] 진행 중인 세션 (" + sessions.size() + "개):");
                    for (GameSession s : sessions) {
                        var p = plugin.getServer().getPlayer(s.getPlayerId());
                        String name = p != null ? p.getName() : s.getPlayerId().toString();
                        sender.sendMessage("  §f" + name + " §7- §e" + s.getTower().getId()
                                + " §7" + s.getCurrentFloor() + "층 §7| 누적: §f"
                                + s.getPendingRewards().size() + "개");
                    }
                }
            }
            case "stop" -> {
                if (args.length < 2) { sender.sendMessage("§c사용법: /lt stop <플레이어>"); return true; }
                var target = plugin.getServer().getPlayer(args[1]);
                if (target == null) { sender.sendMessage("§c플레이어를 찾을 수 없습니다."); return true; }
                if (plugin.getGameManager().forceStop(target.getUniqueId())) {
                    sender.sendMessage("§a" + target.getName() + "의 게임을 강제 종료했습니다.");
                } else {
                    sender.sendMessage("§c해당 플레이어는 게임 중이 아닙니다.");
                }
            }
            case "setblock" -> {
                if (!(sender instanceof Player player)) { sender.sendMessage("§c플레이어만 사용 가능."); return true; }
                if (args.length < 2) { sender.sendMessage("§c사용법: /lt setblock <타워ID>"); return true; }
                String towerId = args[1];
                if (plugin.getTowerManager().getTower(towerId) == null) {
                    sender.sendMessage("§c타워를 찾을 수 없습니다: " + towerId); return true;
                }
                var target = player.getTargetBlock(null, 5);
                BlockPos pos = new BlockPos(
                        player.getWorld().getName(),
                        target.getX(), target.getY(), target.getZ()
                );
                plugin.getTowerManager().setInteractionBlock(towerId, pos);
                sender.sendMessage("§a[럭키타워] " + towerId + " 상호작용 블록 설정: "
                        + pos.getX() + "," + pos.getY() + "," + pos.getZ());
            }
            case "setlight" -> {
                if (!(sender instanceof Player player)) { sender.sendMessage("§c플레이어만 사용 가능."); return true; }
                if (args.length < 3) { sender.sendMessage("§c사용법: /lt setlight <타워ID> <층>"); return true; }
                String towerId = args[1];
                int floor;
                try { floor = Integer.parseInt(args[2]); }
                catch (NumberFormatException e) { sender.sendMessage("§c층 번호가 올바르지 않습니다."); return true; }
                TowerConfig tower = plugin.getTowerManager().getTower(towerId);
                if (tower == null) { sender.sendMessage("§c타워를 찾을 수 없습니다: " + towerId); return true; }
                if (tower.getFloor(floor) == null) { sender.sendMessage("§c해당 층이 없습니다: " + floor); return true; }
                var target = player.getTargetBlock(null, 5);
                BlockPos pos = new BlockPos(
                        player.getWorld().getName(),
                        target.getX(), target.getY(), target.getZ()
                );
                plugin.getTowerManager().addLightToFloor(towerId, floor, pos);
                sender.sendMessage("§a[럭키타워] " + towerId + " " + floor + "층 램프 등록: "
                        + pos.getX() + "," + pos.getY() + "," + pos.getZ());
            }
            case "start"   -> handleStart(sender, args);
            case "jackpot" -> handleJackpot(sender, args);
            default -> sendHelp(sender);
        }
        return true;
    }

    // ─── /lt start ───

    /**
     * /lt start <그룹명> <타워명> <리전명> <층수>
     *
     * ─ 배치 구조 (플레이어가 바라보는 방향 기준):
     *
     *   [플레이어] → [버튼] [램프 0층]
     *                       [램프 1층]  ← 1블록씩 위로 쌓임
     *                       ...
     *                       [램프 N-1층]
     *
     *   • <층수> = 설치할 레드스톤 램프 총 개수 = floors 0 ~ N-1
     *   • 버튼: 전방 1칸, 플레이어 발 Y, 첫 번째 램프 앞 면(WALL) 부착
     *   • 램프 기둥: 전방 2칸, 플레이어 발 Y 부터 1블록씩 수직
     */
    private void handleStart(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c플레이어만 사용 가능합니다.");
            return;
        }
        if (args.length < 5) {
            sender.sendMessage("§c사용법: /lt start <그룹명> <타워명> <리전명> <층수>");
            sender.sendMessage("§7예: /lt start group1 tower_a lucky_tower 15");
            sender.sendMessage("§7<층수> = 설치할 레드스톤 램프 개수 = 총 층 수");
            return;
        }

        String groupId  = args[1];
        String towerId  = args[2];
        String regionId = args[3];
        int floorCount;
        try {
            floorCount = Integer.parseInt(args[4]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§c층수는 숫자여야 합니다."); return;
        }
        if (floorCount < 1 || floorCount > 50) {
            sender.sendMessage("§c층수는 1~50 사이여야 합니다."); return;
        }

        World world  = player.getWorld();
        int playerX  = player.getLocation().getBlockX();
        int playerY  = player.getLocation().getBlockY(); // 발 Y
        int playerZ  = player.getLocation().getBlockZ();

        // 플레이어 수평 방향 → 전방 1칸(버튼), 전방 2칸(램프 기둥)
        BlockFace facing = getCardinalFacing(player);
        int step1X = playerX + facing.getModX();
        int step1Z = playerZ + facing.getModZ();
        int lampX  = step1X  + facing.getModX();
        int lampZ  = step1Z  + facing.getModZ();

        // ── 레드스톤 램프 기둥: floorCount 개, 1블록씩 쌓음 ──
        int placedCount = 0;
        for (int i = 0; i < floorCount; i++) {
            int lampY = playerY + i;
            if (lampY >= world.getMinHeight() && lampY < world.getMaxHeight()) {
                world.getBlockAt(lampX, lampY, lampZ).setType(Material.REDSTONE_LAMP);
                placedCount++;
            }
        }

        // ── 버튼: 전방 1칸, 첫 번째 램프 앞 면(WALL)에 부착 ──
        Block buttonBlock = world.getBlockAt(step1X, playerY, step1Z);
        buttonBlock.setType(Material.OAK_BUTTON);
        Switch sw = (Switch) buttonBlock.getBlockData();
        sw.setAttachedFace(FaceAttachable.AttachedFace.WALL);
        sw.setFacing(facing.getOppositeFace()); // 플레이어 쪽을 향하게
        buttonBlock.setBlockData(sw);

        // 상호작용 블록 = 버튼 위치
        BlockPos interactionPos = new BlockPos(world.getName(), step1X, playerY, step1Z);

        // ── 타워 yml 생성: floors 0 ~ floorCount-1 ──
        boolean created = plugin.getTowerManager().createTower(
                groupId, towerId, regionId, interactionPos,
                lampX, playerY, lampZ,
                floorCount, world.getName());

        if (!created) {
            sender.sendMessage("§c이미 존재하는 타워 ID입니다: §f" + towerId);
            sender.sendMessage("§7다른 이름을 사용하거나 towers/" + towerId + ".yml 파일을 삭제하세요.");
            return;
        }

        plugin.getJackpotManager().loadData();

        sender.sendMessage("§a[럭키타워] 타워 생성 완료!");
        sender.sendMessage("§7타워 ID : §f" + towerId + "  §7그룹: §f" + groupId);
        sender.sendMessage("§7리전    : §f" + regionId);
        sender.sendMessage("§7버튼    : §f" + step1X + ", " + playerY + ", " + step1Z
                + "  §8(방향: " + facing.name() + ")");
        sender.sendMessage("§7램프    : §f" + lampX + ", Y" + playerY + "~Y" + (playerY + placedCount - 1)
                + ", " + lampZ + "  §f" + placedCount + "개");
        sender.sendMessage("§7floors  : §f0 ~ " + (placedCount - 1));
        if (placedCount < floorCount) {
            sender.sendMessage("§c주의: 월드 높이 한계로 " + (floorCount - placedCount) + "개 미설치!");
        }
        sender.sendMessage("§e▶ towers/" + towerId + ".yml 을 수정해 세부 설정을 조정하세요.");
    }

    /**
     * 플레이어 Yaw → 4방향 BlockFace 변환.
     * Minecraft Yaw 기준: 0°=South, 90°=West, 180°=North, 270°=East
     */
    private static BlockFace getCardinalFacing(Player player) {
        float yaw = ((player.getLocation().getYaw() % 360) + 360) % 360;
        if (yaw < 45 || yaw >= 315) return BlockFace.SOUTH;
        if (yaw < 135)              return BlockFace.WEST;
        if (yaw < 225)              return BlockFace.NORTH;
        return                             BlockFace.EAST;
    }

    // ─── 잭팟 서브커맨드 ───
    private void handleJackpot(CommandSender sender, String[] args) {
        // /lt jackpot info [타워ID]
        // /lt jackpot set-amount <타워ID> <금액>
        // /lt jackpot sethologram <타워ID>
        if (args.length < 2) {
            sender.sendMessage("§6[럭키타워 잭팟] 서브커맨드:");
            sender.sendMessage("§f/lt jackpot info [타워ID] §7- 잭팟 현황");
            sender.sendMessage("§f/lt jackpot set-amount <타워ID> <금액> §7- 잭팟 금액 설정");
            sender.sendMessage("§f/lt jackpot sethologram <타워ID> §7- 홀로그램 위치 설정 (바라보는 블록)");
            return;
        }

        switch (args[1].toLowerCase()) {
            case "info" -> {
                if (args.length >= 3) {
                    // 특정 타워 잭팟 정보
                    String towerId = args[2];
                    var tower = plugin.getTowerManager().getTower(towerId);
                    if (tower == null) { sender.sendMessage("§c타워 없음: " + towerId); return; }
                    double amount = plugin.getJackpotManager().getAmount(towerId);
                    sender.sendMessage("§6[잭팟] §f" + towerId + " §7현재 잭팟: §e"
                            + plugin.getVaultHook().format(amount));
                    sender.sendMessage("§7잭팟 활성화: §f" + tower.getJackpotConfig().isEnabled());
                    sender.sendMessage("§7초기 금액: §f" + plugin.getVaultHook().format(tower.getJackpotConfig().getInitialAmount()));
                    sender.sendMessage("§7입장료 적립: §f" + tower.getJackpotConfig().getContributionPercent() + "%");
                } else {
                    // 전체 타워 잭팟 현황
                    sender.sendMessage("§6[럭키타워 잭팟 현황]");
                    for (var tower : plugin.getTowerManager().getAllTowers()) {
                        if (!tower.getJackpotConfig().isEnabled()) continue;
                        double amount = plugin.getJackpotManager().getAmount(tower.getId());
                        sender.sendMessage("  §f" + tower.getId() + " §7→ §e"
                                + plugin.getVaultHook().format(amount));
                    }
                }
            }
            case "set-amount" -> {
                if (args.length < 4) { sender.sendMessage("§c사용법: /lt jackpot set-amount <타워ID> <금액>"); return; }
                var tower = plugin.getTowerManager().getTower(args[2]);
                if (tower == null) { sender.sendMessage("§c타워 없음: " + args[2]); return; }
                try {
                    double amount = Double.parseDouble(args[3]);
                    plugin.getJackpotManager().setAmount(args[2], amount);
                    sender.sendMessage("§a[잭팟] " + args[2] + " 잭팟 금액 설정: §f"
                            + plugin.getVaultHook().format(amount));
                } catch (NumberFormatException e) {
                    sender.sendMessage("§c금액 형식 오류: " + args[3]);
                }
            }
            case "sethologram" -> {
                if (!(sender instanceof Player player)) { sender.sendMessage("§c플레이어만 사용 가능."); return; }
                if (args.length < 3) { sender.sendMessage("§c사용법: /lt jackpot sethologram <타워ID>"); return; }
                var tower = plugin.getTowerManager().getTower(args[2]);
                if (tower == null) { sender.sendMessage("§c타워 없음: " + args[2]); return; }
                var target = player.getTargetBlock(null, 5);
                BlockPos pos = new BlockPos(player.getWorld().getName(),
                        target.getX(), target.getY(), target.getZ());
                plugin.getJackpotManager().setHologramPos(args[2], pos);
                sender.sendMessage("§a[잭팟] " + args[2] + " 홀로그램 위치 설정: "
                        + pos.getX() + "," + pos.getY() + "," + pos.getZ());
            }
            default -> sender.sendMessage("§c알 수 없는 잭팟 서브커맨드: " + args[1]);
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6[럭키타워] 커맨드 도움말");
        sender.sendMessage("§f/lt reload §7- 설정 리로드");
        sender.sendMessage("§f/lt info §7- 진행 중인 세션 목록");
        sender.sendMessage("§f/lt stop <플레이어> §7- 게임 강제 종료");
        sender.sendMessage("§f/lt start <그룹> <타워> <리전> <층높이> §7- 타워 자동 생성");
        sender.sendMessage("§f/lt setblock <타워ID> §7- 상호작용 블록 설정");
        sender.sendMessage("§f/lt setlight <타워ID> <층> §7- 층 램프 블록 등록");
        sender.sendMessage("§f/lt jackpot §7- 잭팟 관리");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("tt.luckytower.admin")) return List.of();

        if (args.length == 1) {
            return List.of("reload", "info", "stop", "start", "setblock", "setlight", "jackpot").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            return switch (args[0].toLowerCase()) {
                case "stop" -> plugin.getServer().getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
                case "setblock", "setlight" -> plugin.getTowerManager().getAllTowers().stream()
                        .map(t -> t.getId())
                        .filter(id -> id.startsWith(args[1]))
                        .collect(Collectors.toList());
                case "jackpot" -> List.of("info", "set-amount", "sethologram").stream()
                        .filter(s -> s.startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
                default -> List.of();
            };
        }

        if (args.length == 3) {
            if (args[0].equalsIgnoreCase("setlight")) {
                TowerConfig tower = plugin.getTowerManager().getTower(args[1]);
                if (tower != null) {
                    List<String> floors = new ArrayList<>();
                    for (int f : tower.getFloors().keySet()) floors.add(String.valueOf(f));
                    return floors;
                }
            }
            if (args[0].equalsIgnoreCase("jackpot") &&
                    (args[1].equalsIgnoreCase("set-amount") || args[1].equalsIgnoreCase("sethologram")
                            || args[1].equalsIgnoreCase("info"))) {
                return plugin.getTowerManager().getAllTowers().stream()
                        .map(t -> t.getId())
                        .filter(id -> id.startsWith(args[2]))
                        .collect(Collectors.toList());
            }
        }

        return List.of();
    }
}
