package whatsmysurroundings.client.Commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import whatsmysurroundings.client.Others.AutoQueryManager;
import whatsmysurroundings.client.Render.BlockRender;
import whatsmysurroundings.client.Render.HighlightMode;
import whatsmysurroundings.client.Render.HighlightType;

import java.util.Comparator;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.CheckedInputStream;
import java.util.stream.Collectors;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

public class wmsentityCommand {
    private static int radius = 5;// 方形半径
    private static int blockTaskId = -1;
    private static int internal = 20;// 默认自动查询间隔
    private static boolean enableAutoQuery = false;
    private static List<Entity> TargetEntity = new ArrayList<>();

    // 半径建议
    private static final SuggestionProvider<FabricClientCommandSource> RADIUS_SUGGESTIONS = (context,
            builder) -> {
        List<Integer> suggestions = Arrays.asList(5, 100, 16, 160);
        for (int value : suggestions) {
            builder.suggest(String.valueOf(value));
        }
        return builder.buildFuture();
    };

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(
                    ClientCommands.literal("wmsentity")
                            // '/wmsentity' 直接输入无结果，返回值为1
                            .executes(context -> {
                                return nullCommand(context);
                            })

                            // '/wmsentity set <radius>' 设置查询方形半径
                            .then(ClientCommands.literal("set")
                                    .then(ClientCommands.argument("radius", IntegerArgumentType.integer())
                                            .suggests(RADIUS_SUGGESTIONS)
                                            .executes(context -> {
                                                return setRadius(context,
                                                        IntegerArgumentType.getInteger(context, "radius"));
                                            }))

                            )

                            // '/wmsentity target ...'
                            .then(ClientCommands.literal("target")
                                    // '/wmsentity target add <block_id>' 添加目标方块
                                    .then(ClientCommands.literal("add")
                                            .then(ClientCommands
                                                    .argument("block_id", BlockStateArgument.block(registryAccess))
                                                    .suggests(new BlockSuggestionProvider())
                                                    .executes(context -> {
                                                        BlockInput blockInput = context.getArgument("block_id",
                                                                BlockInput.class);
                                                        Block newblock = blockInput.getState().getBlock();
                                                        return addTargetBlock(context, newblock);
                                                    })))

                                    // '/wmsentity target remove <block_id>' 删除目标方块
                                    .then(ClientCommands.literal("remove")
                                            .then(ClientCommands
                                                    .argument("block_id", BlockStateArgument.block(registryAccess))
                                                    .suggests(TARGETE_SUGGESTIONS)
                                                    .executes(context -> {
                                                        BlockInput blockInput = context.getArgument("block_id",
                                                                BlockInput.class);
                                                        Block rmvblock = blockInput.getState().getBlock();
                                                        return removeTargetBlock(context, rmvblock);
                                                    })))

                                    // '/wmsentity target list' 列出已有的目标方块
                                    .then(ClientCommands.literal("list")
                                            .executes(context -> {
                                                return showTargetBlocks(context);
                                            }))

                            )

                            // '/wmsentity find' 查询仅目标方块
                            .then(ClientCommands.literal("find")
                                    .executes(context -> {
                                        return findTargetBlocksOnly(context);
                                    })

                                    // '/wmsentity find *' 查询所有非空气方块
                                    .then(ClientCommands.literal("*")
                                            .executes(context -> {
                                                return findAllBlocks(context);
                                            }))

                                    // '/wmsentity find [<block_id>]' 加入并查询目标方块
                                    .then(ClientCommands.argument("entity_id", BlockStateArgument.block(registryAccess))
                                            .suggests(new BlockSuggestionProvider())
                                            .executes(context -> {
                                                EntityInput blockInput = context.getArgument("block_id",
                                                        BlockInput.class);
                                                Block newblock = blockInput.getState().getBlock();

                                                return findandAddTargetBlocksOnly(context, newblock);
                                            }))

                            )

                            // '/wmsentity clear' 清除显示
                            .then(ClientCommands.literal("clear")
                                    .executes(context -> {
                                        return clearBlockHighlight(context);
                                    })

                            )

                            // '/wmsentity auto' 切换自动查询
                            .then(ClientCommands.literal("auto")
                                    .executes(context -> {
                                        return toggleAutoFind(context);
                                    })

                                    // '/wmsentity auto [<internal>]' 设置查询间隔并开启自动查询
                                    .then(ClientCommands.argument("internal", IntegerArgumentType.integer())
                                            .executes(context -> {
                                                return setInternal(context,
                                                        IntegerArgumentType.getInteger(context, "internal"));
                                            }))

                            )

            );
        });
    }

    // 加入或删除任务
    private static void CheckState(CommandContext<FabricClientCommandSource> context) {
        if (enableAutoQuery) {
            // 如果已有任务，先移除再注册
            if (blockTaskId != -1) {
                AutoQueryManager.removeTask(blockTaskId);
                blockTaskId = -1;
            }

            // 注册新任务
            blockTaskId = AutoQueryManager.addTask(
                    () -> autofindTargetBlocksOnly(context), // 查询逻辑
                    internal);

            context.getSource().sendFeedback(
                    Component.literal("§e[WMS] 方块自动查询已开启，间隔 " + internal / 20 + " s(" + internal + ")"));
        } else {
            if (blockTaskId != -1) {
                AutoQueryManager.removeTask(blockTaskId);
                blockTaskId = -1;
            }
            context.getSource().sendFeedback(
                    Component.literal("§e[WMS] 方块自动查询已关闭"));
        }
    }

    // 切换自动查询
    private static int toggleAutoFind(CommandContext<FabricClientCommandSource> context) {
        enableAutoQuery = !enableAutoQuery;
        CheckState(context);
        return 1;
    }

    // 加入并查询目标方块
    private static int findandAddTargetBlocksOnly(CommandContext<FabricClientCommandSource> context, Block new_block) {
        boolean has_found = false;
        for (Block target : TargetBlocks)
            if (BuiltInRegistries.BLOCK.wrapAsHolder(target).getRegisteredName().equals(
                    BuiltInRegistries.BLOCK.wrapAsHolder(new_block).getRegisteredName()))
                has_found = true;

        if (!has_found)
            TargetBlocks.add(new_block);

        int totaltargetblocks = findTargetBlocksOnly(context);

        if (!has_found)
            TargetBlocks.remove(new_block);

        return totaltargetblocks;
    }

    // 查询仅目标方块
    private static int findTargetBlocksOnly(CommandContext<FabricClientCommandSource> context) {
        if (TargetBlocks.isEmpty()) {
            context.getSource().sendFeedback(
                    Component.literal("§e[WMS] 请先使用 /wmsblock target add 添加目标方块"));
            return 0;
        }

        LocalPlayer player = context.getSource().getPlayer();

        Level world = player.level();
        BlockPos centerPos = player.blockPosition();

        Map<Block, List<BlockPos>> foundMap = new HashMap<>();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos pos = centerPos.offset(dx, dy, dz);
                    BlockState state = world.getBlockState(pos);
                    if (!state.isAir()) {
                        Block block = state.getBlock();
                        if (TargetBlocks.contains(block)) {
                            foundMap.computeIfAbsent(block, k -> new ArrayList<>()).add(pos);

                            BlockRender.addHighlight(HighlightType.BLOCK, HighlightMode.MANUAL, pos, 0.2f, 0.7f, 0.7f,
                                    0.8f, -1);
                        }
                    }
                }
            }
        }

        if (foundMap.isEmpty()) {
            context.getSource().sendFeedback(
                    Component.literal("§e[WMS] 在半径 " + radius + " 内未找到任何目标方块"));
            return 0;
        }

        // 输出结果
        context.getSource().sendFeedback(
                Component.literal(String.format("§a[WMS] 在半径 %d 内找到 %d 种目标方块:", radius, foundMap.size())));
        int totaltargetblocks = 0;

        for (Map.Entry<Block, List<BlockPos>> entry : foundMap.entrySet()) {
            Block block = entry.getKey();
            List<BlockPos> positions = entry.getValue();
            String blockName = block.getName().getString();
            String registryName = BuiltInRegistries.BLOCK.wrapAsHolder(block).getRegisteredName();

            // 输出坐标列表（每行2个坐标）
            StringBuilder text = new StringBuilder(
                    String.format("  §f- %s §7(%s):\n    ", blockName, registryName));
            for (int i = 0; i < positions.size(); i++) {
                BlockPos pos = positions.get(i);
                text.append(String.format("§7[§c%d§7, §a%d§7, §b%d§7]",
                        pos.getX(), pos.getY(), pos.getZ()));
                if (i < positions.size() - 1)
                    text.append("§7,    ");
                else
                    text.append("§7.");

                // 每2个坐标换行
                if ((i + 1) % 2 == 0 && i < positions.size() - 1)
                    text.append("\n    ");
            }
            totaltargetblocks += positions.size();

            context.getSource().sendFeedback(Component.literal(text.toString()));
        }

        return totaltargetblocks;
    }

    // 查询仅目标方块
    private static int autofindTargetBlocksOnly(CommandContext<FabricClientCommandSource> context) {
        if (TargetBlocks.isEmpty()) {
            context.getSource().sendFeedback(
                    Component.literal("§e[WMS 自动] 请先使用 /wmsblock target add 添加目标方块"));
            toggleAutoFind(context);
            return 0;
        }

        LocalPlayer player = context.getSource().getPlayer();

        Level world = player.level();
        BlockPos centerPos = player.blockPosition();

        Map<Block, List<BlockPos>> foundMap = new HashMap<>();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos pos = centerPos.offset(dx, dy, dz);
                    BlockState state = world.getBlockState(pos);
                    if (!state.isAir()) {
                        Block block = state.getBlock();
                        if (TargetBlocks.contains(block)) {
                            foundMap.computeIfAbsent(block, k -> new ArrayList<>()).add(pos);

                            BlockRender.addHighlight(HighlightType.BLOCK, HighlightMode.AUTO, pos, 0.7f, 0.3f, 0.1f,
                                    0.8f, internal);
                        }
                    }
                }
            }
        }

        if (foundMap.isEmpty()) {
            context.getSource().sendFeedback(
                    Component.literal("§e[WMS 自动] 在半径 " + radius + " 内未找到任何目标实体"));
            return 0;
        }

        // 输出结果
        context.getSource().sendFeedback(
                Component.literal(String.format("§a[WMS 自动] 在半径 %d 内找到 %d 种目标方块:", radius, foundMap.size())));
        int totaltargetblocks = 0;

        for (Map.Entry<Block, List<BlockPos>> entry : foundMap.entrySet()) {
            Block block = entry.getKey();
            List<BlockPos> positions = entry.getValue();
            String blockName = block.getName().getString();
            String registryName = BuiltInRegistries.BLOCK.wrapAsHolder(block).getRegisteredName();

            // 输出坐标列表（每行2个坐标）
            StringBuilder text = new StringBuilder(
                    String.format("  §f- %s §7(%s):\n    ", blockName, registryName));
            for (int i = 0; i < positions.size(); i++) {
                BlockPos pos = positions.get(i);
                text.append(String.format("§7[§c%d§7, §a%d§7, §b%d§7]",
                        pos.getX(), pos.getY(), pos.getZ()));
                if (i < positions.size() - 1)
                    text.append("§7,    ");
                else
                    text.append("§7.");

                // 每2个坐标换行
                if ((i + 1) % 2 == 0 && i < positions.size() - 1)
                    text.append("\n    ");
            }
            totaltargetblocks += positions.size();

            context.getSource().sendFeedback(Component.literal(text.toString()));
        }

        return totaltargetblocks;
    }

    // 查询所有非空气方块
    private static int findAllBlocks(CommandContext<FabricClientCommandSource> context) {
        LocalPlayer player = context.getSource().getPlayer();

        Level world = player.level();
        BlockPos centerPos = player.blockPosition();

        int totalBlocks = 0;
        int targetCount = 0;

        // 先统计数量
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos pos = centerPos.offset(dx, dy, dz);
                    BlockState state = world.getBlockState(pos);
                    if (!state.isAir()) {
                        totalBlocks++;
                        if (TargetBlocks.contains(state.getBlock())) {
                            targetCount++;

                            BlockRender.addHighlight(HighlightType.BLOCK, HighlightMode.MANUAL, pos, 0.2f, 0.7f, 0.7f,
                                    0.8f, -1);
                        }
                    }
                }
            }
        }

        context.getSource().sendFeedback(
                Component.literal(String.format(
                        "§a[WMS] 半径 %d 内共有 §e%d §a个方块，其中目标方块 §e%d §a个",
                        radius, totalBlocks, targetCount)));

        if (totalBlocks == 0) {
            context.getSource().sendFeedback(
                    Component.literal("§e[WMS] 半径范围内没有非空气方块"));
            return 1;
        }

        int maxDisplay = 16; // 最多显示方块数量
        int displayed = 0;
        boolean hasMore = false;
        StringBuilder text = new StringBuilder("\n");

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos pos = centerPos.offset(dx, dy, dz);
                    BlockState state = world.getBlockState(pos);
                    if (!state.isAir()) {
                        if (displayed >= maxDisplay) {
                            hasMore = true;
                            break;
                        }
                        Entity block = state.getBlock();
                        String blockName = block.getName().getString();
                        boolean isTarget = TargetBlocks.contains(block);
                        String prefix = isTarget ? "\n§f  [" : "\n§7  [";
                        String suffix = isTarget ? "§f] " : "§7] ";

                        text.append(prefix);
                        text.append(pos.getX());
                        text.append(", ");
                        text.append(pos.getY());
                        text.append(", ");
                        text.append(pos.getZ());
                        text.append(suffix);
                        text.append(blockName);

                        displayed++;
                    }
                }
                if (hasMore)
                    break;
            }
            if (hasMore)
                break;
        }
        context.getSource().sendFeedback(
                Component.literal(text.toString()));

        if (hasMore) {
            context.getSource().sendFeedback(
                    Component.literal(String.format(
                            "§e[WMS] ... 还有 %d 个实体未显示",
                            totalBlocks - maxDisplay)));
        }

        return totalBlocks;
    }

    // 加入新的目标方块
    private static int addTargetBlock(CommandContext<FabricClientCommandSource> context, Entity new_block) {
        for (Entity target : TargetBlocks) {
            if (BuiltInRegistries.BLOCK.wrapAsHolder(target).getRegisteredName().equals(
                    BuiltInRegistries.BLOCK.wrapAsHolder(new_block).getRegisteredName())) {
                context.getSource()
                        .sendFeedback(Component.literal(String.format(
                                "§c[WMS 错误] 你已经提供了该目标方块 (%s)", new_block)));
                return 0;
            }
        }
        TargetBlocks.add(new_block);
        context.getSource()
                .sendFeedback(Component.literal(String.format(
                        "§a[WMS] 增加新目标方块 (%s)",
                        BuiltInRegistries.BLOCK.wrapAsHolder(new_block).getRegisteredName())));
        return 1;
    }

    // 显示已有的目标方块
    private static int showTargetBlocks(CommandContext<FabricClientCommandSource> context) {
        int size = TargetBlocks.size();
        context.getSource()
                .sendFeedback(Component.literal((String.format(
                        "§a[WMS] 已有的目标方块: %d个", size))));
        if (size == 0)
            return 0;

        int counter = 1;
        for (Entity target : TargetBlocks) {
            if (counter != size)
                context.getSource()
                        .sendFeedback(Component.literal(String.format(
                                "§7  %2d : §f%s §7,", counter,
                                BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(target).getRegisteredName())));
            else
                context.getSource()
                        .sendFeedback(Component.literal(String.format(
                                "§7  %2d : §f%s §7.", counter,
                                BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(target).getRegisteredName())));
            counter++;
        }
        return size;
    }

    // 删除目标方块
    private static int removeTargetBlock(CommandContext<FabricClientCommandSource> context, Entity tormv_entity) {
        /*
         * boolean has_found = false;
         * for (Block target : TargetBlocks)
         * if (BuiltInRegistries.BLOCK.wrapAsHolder(target).getRegisteredName().equals(
         * BuiltInRegistries.BLOCK.wrapAsHolder(tormv_block).getRegisteredName()))
         * has_found = true;
         */
        if (TargetEntities.remove(tormv_entity)) {
            context.getSource()
                    .sendFeedback(Component.literal(String.format(
                            "§e[WMS] 已移除目标实体 (§m%s§r§e)",
                            BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(tormv_entity).getRegisteredName())));
            return 1;
        } else
            return 0;
    }

    // 设置方形半径
    private static int setRadius(CommandContext<FabricClientCommandSource> context, int new_radius) {
        if (new_radius < 0) {
            context.getSource()
                    .sendFeedback(Component.literal(String.format(
                            "§c[WMS 错误] 方形半径参数 radius 小于0！(%d，现:%d)", new_radius, radius)));
            return 0;
        }

        radius = new_radius;
        if (radius > 127) {
            context.getSource()
                    .sendFeedback(Component.literal(String.format(
                            "§e[WMS 警告] 方形半径参数 radius 大于128，可能查询造成卡顿！(现:%d)", radius)));
        }
        if (radius > 160) {
            radius = 160;
            context.getSource()
                    .sendFeedback(Component.literal(String.format(
                            "§e[WMS 警告] 方形半径参数 radius 已限制到160，过大可能查询造成卡顿！(现:%d)", radius)));
        }

        context.getSource()
                .sendFeedback(Component.literal(String.format(
                        "§a[WMS] 方形半径参数 radius 现变为 %d", radius)));

        return radius;
    }

    // 设置间隔
    private static int setInternal(CommandContext<FabricClientCommandSource> context, int new_internal) {
        if (new_internal < 0) {
            context.getSource()
                    .sendFeedback(Component.literal(String.format(
                            "§c[WMS 错误] 间隔参数 internal 小于0！(%d，现:%d)", new_internal, internal)));
            return 0;
        }

        internal = new_internal;
        if (internal < 20) {
            context.getSource()
                    .sendFeedback(Component.literal(String.format(
                            "§e[WMS 警告] 间隔参数 internal 小于20，可能查询造成卡顿！(现:%d)", internal)));
        }

        context.getSource()
                .sendFeedback(Component.literal(String.format(
                        "§a[WMS] 间隔参数 internal 现变为 %d", internal)));

        enableAutoQuery = true;
        CheckState(context);
        return internal;
    }

    // 清除实体高亮
    private static int clearEntityHighlight(CommandContext<FabricClientCommandSource> context) {
        context.getSource()
                .sendFeedback(Component.literal(String.format(
                        "§a[WMS] 已清除所有实体高亮")));

        BlockRender.clearHighlights(HighlightType.ENTITY, HighlightMode.MANUAL);
        BlockRender.clearHighlights(HighlightType.ENTITY, HighlightMode.AUTO);
        return 1;
    }

    // 空指令
    private static int nullCommand(CommandContext<FabricClientCommandSource> context) {
        context.getSource().sendFeedback(
                Component.literal("§7[WMS Debug] Called '/wmstest' with no arguments."));
        return 1;
    }

}