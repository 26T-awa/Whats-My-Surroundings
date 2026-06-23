package whatsmysurroundings.client.Commands;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import whatsmysurroundings.client.Commands.WMSCommands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;

public class wmshelpCommand {
    // 命令建议
    private static final SuggestionProvider<FabricClientCommandSource> COMMAND_SUGGESTIONS = (context,
            builder) -> {
        List<String> suggestions = WMSCommands.getWMSCommands();
        for (String value : suggestions) {
            builder.suggest(value);
        }
        return builder.buildFuture();
    };

    public static void register() {
        WMSCommands.addCommandString("wmshelp");

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(
                    ClientCommands.literal("wmshelp")
                            // '/wmshelp'
                            .executes(context -> {
                                return showAllCommand(context);
                            })

                            // '/wmshelp [<command>]'
                            .then(ClientCommands.argument("command", StringArgumentType.string())
                                    .suggests(COMMAND_SUGGESTIONS)
                                    .executes(context -> {
                                        return CommandHelp(context, StringArgumentType.getString(context, "command"));
                                    }))

            );
        });
    }

    // 列出已有的全部命令
    private static int showAllCommand(CommandContext<FabricClientCommandSource> context) {
        int count = 1;
        context.getSource()
                .sendFeedback(Component.literal(
                        "§a[WMS] 现有的所有命令："));

        for (String command : WMSCommands.getWMSCommands()) {
            context.getSource()
                    .sendFeedback(Component.literal(String.format(
                            "  %d. %s", count, command)));
            count++;
        }
        return 1;
    }

    // 获得指定命令的用法
    private static int CommandHelp(CommandContext<FabricClientCommandSource> context, String command) {
        switch (command) {

            case "wmsblock": {
                context.getSource()
                        .sendFeedback(Component.literal(
                                "/wmsblock用法如下:"));

                context.getSource()
                        .sendFeedback(Component.literal("" +
                                "/wmsblock find§7-查询所有非空气方块§r\n" +
                                "/wmsblock find *§7-查询仅目标方块§r\n" +
                                "/wmsblock set <radius>§7-设置方形半径§r\n" +
                                "/wmsblock target add <block_id>§7-加入新的目标方块§r\n" +
                                "/wmsblock target list§7-显示已有的目标方块§r\n" +
                                "/wmsblock target remove <block_id>§7-删除目标方块§r\n"));
                break;
            }

            case "wmshelp": {
                context.getSource()
                        .sendFeedback(Component.literal(
                                "/wmshelp用法如下:"));

                context.getSource()
                        .sendFeedback(Component.literal("" +
                                "/wmshelp§7-列出已有的全部命令§r\n" +
                                "/wmshelp <command>§7-获得指定命令的用法§r\n"));
                break;
            }

            default: {
                return 0;
            }
        }

        return 1;
    }
}