package whatsmysurroundings.client.Commands;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

public class TestCommand {

    // 注册方法：在客户端初始化时调用
    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(
                    ClientCommands.literal("wmstest")
                            // '/wmstest'
                            .executes(context -> {
                                context.getSource().sendFeedback(
                                        Component.literal("[WMS Debug] Called '/wmstest' with no arguments."));
                                return 1;
                            })
                            // '/wmstest [<radius>]'
                            .then(ClientCommands.argument("radius", IntegerArgumentType.integer())
                                    .executes(context -> {
                                        return TestWithArgs(context, IntegerArgumentType.getInteger(context, "radius"));
                                    })));
        });
    }

    public static int TestWithArgs(CommandContext<FabricClientCommandSource> context, int radius) {
        var Position = context.getSource().getPosition();

        context.getSource()
                .sendFeedback(Component.literal(String.format(
                        "\u00a77[WMS Debug] Called '/wmstest <radius>' at Position:%.2f, %.2f, %.2f",
                        Position.x, Position.y, Position.z)));

        if (radius < 0) {
            context.getSource()
                    .sendFeedback(Component.literal(
                            "\u00a7c[WMS 错误] 半径参数 radius 小于0！"));
            return 0;
        }

        LocalPlayer player = context.getSource().getPlayer();
        Level world = player.level();
        BlockPos centerPos = player.blockPosition();
        int blockCount = 0;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos targetPos = centerPos.offset(dx, dy, dz);
                    BlockState state = world.getBlockState(targetPos);

                    // 只统计非空气方块
                    if (!state.isAir()) {
                        blockCount++;
                        context.getSource()
                                .sendFeedback(Component.literal(String.format(
                                        "\u00a7a[WMS] 位于%d %d %d的方块信息是：%s",
                                        targetPos.getX(), targetPos.getY(), targetPos.getZ(),state.getBlock().getName().getString())));
                    }
                }
            }
        }

        // 3. 发送结果
        context.getSource().sendFeedback(
                Component.literal(String.format(
                        "\u00a7a在半径 %d 范围内共有 \u00a7e%d \u00a7a个非空气方块",
                        radius, blockCount)));
        return 1;
    }
}