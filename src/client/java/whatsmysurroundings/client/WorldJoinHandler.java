package whatsmysurroundings.client;

import net.minecraft.network.chat.Component;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

@Environment(EnvType.CLIENT)
public class WorldJoinHandler {

    private static boolean hasJoined = false;

    public static void register() {

        // 注册一个 Tick 事件监听器，用于检测玩家是否已进入世界
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // 当客户端有玩家对象，且之前未发送过消息时
            if (client.player != null && !hasJoined) {
                hasJoined = true;
                // 在聊天框发送消息
                client.player.sendSystemMessage(Component.literal(
                        "§a你已成功加载 Whats My Surroudings 模组！\n键入 '§b§n/wmshelp§r§a' 获得帮助。§r"));
                // 控制台也输出一条，方便调试
                System.out.println("[WMS] 加载成功。");
            }
        });
    }
}