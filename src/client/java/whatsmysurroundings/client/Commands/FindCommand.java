package whatsmysurroundings.client.Commands;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.text.html.HTML.Tag;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.arguments.blocks.BlockInput;
import net.minecraft.commands.arguments.blocks.BlockStateArgument;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;

public class FindCommand {
    private static int radius = 1;// 方形半径
    private static List<Block> TargetBlocks = new ArrayList<>();

    // 半径建议
    private static final SuggestionProvider<FabricClientCommandSource> RADIUS_SUGGESTIONS = (context,
            builder) -> {
        List<Integer> suggestions = Arrays.asList(1, 3, 5);
        for (int value : suggestions) {
            builder.suggest(String.valueOf(value));
        }
        return builder.buildFuture();
    };

    // 已有目标方块建议
    private static final SuggestionProvider<FabricClientCommandSource> TARGETBLOCK_SUGGESTIONS = (context,
            builder) -> {
        List<Block> suggestions = TargetBlocks;
        for (Block value : suggestions) {
            builder.suggest(BuiltInRegistries.BLOCK.wrapAsHolder(value).getRegisteredName());
        }
        return builder.buildFuture();
    };

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(
                    ClientCommands.literal("wmsblock")
                            // '/wmsblock' 直接输入无结果，返回值为1
                            .executes(context -> {
                                return nullCommand(context);
                            })

                            // '/wmsblock set <radius>' 设置查询方形半径
                            .then(ClientCommands.literal("set")
                                    .then(ClientCommands.argument("radius", IntegerArgumentType.integer())
                                            .suggests(RADIUS_SUGGESTIONS)
                                            .executes(context -> {
                                                return setRadius(context,
                                                        IntegerArgumentType.getInteger(context, "radius"));
                                            })))

                            // '/wmsblock target ...'
                            .then(ClientCommands.literal("target")
                                    // '/wmsblock target add <block_id>' 添加目标方块
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

                                    // '/wmsblock target remove <block_id>' 删除目标方块
                                    .then(ClientCommands.literal("remove")
                                            .then(ClientCommands
                                                    .argument("block_id", BlockStateArgument.block(registryAccess))
                                                    .suggests(TARGETBLOCK_SUGGESTIONS)
                                                    .executes(context -> {
                                                        BlockInput blockInput = context.getArgument("block_id",
                                                                BlockInput.class);
                                                        Block rmvblock = blockInput.getState().getBlock();
                                                        return removeTargetBlock(context, rmvblock);
                                                    })))

                                    // '/wmsblock target list' 列出已有的目标方块
                                    .then(ClientCommands.literal("list")
                                            .executes(context -> {
                                                return showTargetBlocks(context);
                                            }))

                            )

                            // '/wmsblock find' 开始查询
                            .then(ClientCommands.literal("find")
                                    .executes(context -> {
                                        return findTargetBlocksOnly(context);
                                    })
                                    .then(ClientCommands.literal("*")
                                            .executes(context -> {
                                                return findAllBlocks(context);
                                            })))

            );
        });
    }

    // 开始查询
    private static int findTargetBlocksOnly(CommandContext<FabricClientCommandSource> context) {
        if (TargetBlocks.isEmpty()) {
            context.getSource().sendFeedback(
                    Component.literal("\u00a7e[WMS] 请先使用 /wmsblock target add 添加目标方块"));
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
                        }
                    }
                }
            }
        }

        if (foundMap.isEmpty()) {
            context.getSource().sendFeedback(
                    Component.literal("\u00a7e[WMS] 在半径 " + radius + " 内未找到任何目标方块"));
            return 1;
        }

        // 输出结果
        context.getSource().sendFeedback(
                Component.literal(String.format("\u00a7a[WMS] 在半径 %d 内找到 %d 种目标方块:", radius, foundMap.size())));

        for (Map.Entry<Block, List<BlockPos>> entry : foundMap.entrySet()) {
            Block block = entry.getKey();
            List<BlockPos> positions = entry.getValue();
            String blockName = block.getName().getString();
            String registryName = BuiltInRegistries.BLOCK.wrapAsHolder(block).getRegisteredName();

            // 输出方块名称
            context.getSource().sendFeedback(
                    Component.literal(String.format("  \u00a7f- %s \u00a77(%s):", blockName, registryName)));

            // 输出坐标列表（每行最多3个坐标）
            StringBuilder line = new StringBuilder("      ");
            for (int i = 0; i < positions.size(); i++) {
                BlockPos pos = positions.get(i);
                line.append(String.format("[%d, %d, %d]", pos.getX(), pos.getY(), pos.getZ()));
                if (i < positions.size() - 1) {
                    line.append(", ");
                }
                // 每3个坐标换行
                if ((i + 1) % 3 == 0 && i < positions.size() - 1) {
                    context.getSource().sendFeedback(Component.literal(line.toString()));
                    line = new StringBuilder("      ");
                }
            }
            if (line.length() > 6) {
                context.getSource().sendFeedback(Component.literal(line.toString()));
            }
        }

        return 1;
    }

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
                        }
                    }
                }
            }
        }

        context.getSource().sendFeedback(
                Component.literal(String.format(
                        "\u00a7a[WMS] 半径 %d 内共有 \u00a7e%d \u00a7a个方块，其中目标方块 \u00a7e%d \u00a7a个",
                        radius, totalBlocks, targetCount)));

        if (totalBlocks == 0) {
            context.getSource().sendFeedback(
                    Component.literal("\u00a7e[WMS] 半径范围内没有非空气方块"));
            return 1;
        }

        int maxDisplay = 16; // 最多显示方块数量
        int displayed = 0;
        boolean hasMore = false;

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
                        Block block = state.getBlock();
                        String blockName = block.getName().getString();
                        boolean isTarget = TargetBlocks.contains(block);
                        String prefix = isTarget ? "\u00a7a* " : "\u00a77  ";
                        String suffix = isTarget ? "\u00a7a" : "\u00a77";

                        context.getSource().sendFeedback(
                                Component.literal(String.format(
                                        "%s[%d, %d, %d] %s%s",
                                        prefix,
                                        pos.getX(), pos.getY(), pos.getZ(),
                                        suffix,
                                        blockName)));
                        displayed++;
                    }
                }
                if (hasMore)
                    break;
            }
            if (hasMore)
                break;
        }

        if (hasMore) {
            context.getSource().sendFeedback(
                    Component.literal(String.format(
                            "\u00a7e[WMS] ... 还有 %d 个方块未显示",
                            totalBlocks - maxDisplay)));
        }

        return 1;
    }

    // 加入新的目标方块
    private static int addTargetBlock(CommandContext<FabricClientCommandSource> context, Block new_block) {
        for (Block target : TargetBlocks) {
            if (BuiltInRegistries.BLOCK.wrapAsHolder(target).getRegisteredName().equals(
                    BuiltInRegistries.BLOCK.wrapAsHolder(new_block).getRegisteredName())) {
                context.getSource()
                        .sendFeedback(Component.literal(String.format(
                                "\u00a7c[WMS 错误] 你已经提供了该目标方块 (%s)", new_block)));
                return 0;
            }
        }
        TargetBlocks.add(new_block);
        context.getSource()
                .sendFeedback(Component.literal(String.format(
                        "\u00a7a[WMS] 增加新目标方块 (%s)",
                        BuiltInRegistries.BLOCK.wrapAsHolder(new_block).getRegisteredName())));
        return 1;
    }

    // 显示已有的目标方块
    private static int showTargetBlocks(CommandContext<FabricClientCommandSource> context) {
        int size = TargetBlocks.size();
        context.getSource()
                .sendFeedback(Component.literal((String.format(
                        "\u00a7a[WMS] 已有的目标方块: %d个", size))));
        if (size == 0)
            return 0;

        int counter = 1;
        for (Block target : TargetBlocks) {
            if (counter != size)
                context.getSource()
                        .sendFeedback(Component.literal(String.format(
                                "\u00a77  %2d : \u00a7f%s \u00a77,", counter,
                                BuiltInRegistries.BLOCK.wrapAsHolder(target).getRegisteredName())));
            else
                context.getSource()
                        .sendFeedback(Component.literal(String.format(
                                "\u00a77  %2d : \u00a7f%s \u00a77.", counter,
                                BuiltInRegistries.BLOCK.wrapAsHolder(target).getRegisteredName())));
            counter++;
        }
        return 1;
    }

    // 删除目标方块
    private static int removeTargetBlock(CommandContext<FabricClientCommandSource> context, Block tormv_block) {
        boolean has_found = false;
        for (Block target : TargetBlocks)
            if (BuiltInRegistries.BLOCK.wrapAsHolder(target).getRegisteredName().equals(
                    BuiltInRegistries.BLOCK.wrapAsHolder(tormv_block).getRegisteredName()))
                has_found = true;

        if (has_found) {
            TargetBlocks.remove(tormv_block);
            context.getSource()
                    .sendFeedback(Component.literal(String.format(
                            "\u00a7e[WMS] 已移除目标方块 (\u00a7m%s\u00a7r\u00a7e)",
                            BuiltInRegistries.BLOCK.wrapAsHolder(tormv_block).getRegisteredName())));
            return 1;
        } else
            return 0;
    }

    // 设置方形半径
    private static int setRadius(CommandContext<FabricClientCommandSource> context, int new_radius) {
        if (radius < 0) {
            context.getSource()
                    .sendFeedback(Component.literal(String.format(
                            "\u00a7c[WMS 错误] 方形半径参数 radius 小于0！(%d，现:%d)", new_radius, radius)));
            return 0;
        }

        radius = new_radius;
        context.getSource()
                .sendFeedback(Component.literal(String.format(
                        "\u00a7a[WMS] 方形半径参数 radius 现变为 %d)", radius)));

        return 1;
    }

    // 空指令
    private static int nullCommand(CommandContext<FabricClientCommandSource> context) {
        context.getSource().sendFeedback(
                Component.literal("\u00a77[WMS Debug] Called '/wmstest' with no arguments."));
        return 1;
    }

}