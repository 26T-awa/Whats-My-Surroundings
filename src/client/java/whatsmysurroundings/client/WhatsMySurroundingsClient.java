package whatsmysurroundings.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import whatsmysurroundings.client.WMSCommands.WMSCommands;
import whatsmysurroundings.client.WorldRender.KeyBindings;

@Environment(EnvType.CLIENT)
public class WhatsMySurroundingsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // 注册玩家进入世界的事件处理器
        WorldJoinHandler.register();

        // 2. 注册按键绑定
        KeyBindings.register();

        // 3. 注册按键监听器
        KeyBindings.registerTickListener();

        // 4. 注册自定义命令
        WMSCommands.init();

        System.out.println("[WMS] 客户端初始化完成，已注册事件监听器");
    }
}