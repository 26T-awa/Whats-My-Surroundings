package whatsmysurroundings.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class MainScreen extends Screen {

    // 必须调用 super(Component title)
    public MainScreen() {
        super(Component.literal("我的主屏幕")); // 显式调用父类构造函数
    }

    @Override
    protected void init() {
        super.init();

        // 创建按钮：点击后在聊天框发送消息
        Button sendMessageButton = Button.builder(
                Component.literal("Test"),
                button -> {
                    // 获取当前客户端实例
                    Minecraft client = Minecraft.getInstance();
                    if (client.player != null) {
                        // 方式一：发送普通聊天消息（会显示在聊天框，其他玩家可见）
                        client.player.connection.sendChat("Hello from my mod!");

                        // 方式二：发送系统消息（仅自己可见，通常用于提示）
                        // client.player.sendSystemMessage(Component.literal("消息已发送！"));

                        System.out.println("[WMS] 按钮被点击，已发送消息");
                    }
                }).bounds(this.width / 2 - 50, this.height / 2 - 10, 100, 20).build();

        this.addRenderableWidget(sendMessageButton);
    }
}