package whatsmysurroundings.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.chat.Component;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

@Environment(EnvType.CLIENT)
public class KeyBindings {
    /* 非函数成员 */
    private static final int KEY_R = GLFW.GLFW_KEY_R;

    /* 定义一个按键绑定，默认设置为 'R' 键 */
    private static KeyMapping openGuiKey = new KeyMapping(
            "key.whatsmysurroundings.open_gui",
            InputConstants.Type.KEYSYM,
            KEY_R,
            KeyMapping.Category.MISC);

    /* 函数成员 */
    // 注册按键绑定
    public static void register() {
        KeyMappingHelper.registerKeyMapping(openGuiKey);

        System.out.println("[WMS] 已注册按键绑定: R");
    }

    // 设置按键绑定
    public static void setNewKey(int key_value) {
        InputConstants.Key newKey = InputConstants.Type.KEYSYM.getOrCreate(key_value);
        openGuiKey.setKey(newKey);
        register();

        System.out.println("[WMS] 已绑定新按键");
    }

    // 重置按键绑定，默认为'R'
    public static void resetKey() {
        InputConstants.Key newKey = InputConstants.Type.KEYSYM.getOrCreate(KEY_R);
        openGuiKey.setKey(newKey);
        register();

        System.out.println("[WMS] 已重置按键");
    }

    public static void registerTickListener() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // 当玩家按下测试键时
            if (openGuiKey != null && openGuiKey.consumeClick() && client.player != null) {
                client.setScreen(new MainScreen());

                client.player.sendSystemMessage(Component.literal("Text"));
                System.out.println("[WMS] 玩家按下了测试键");

            }
        });
    }

}