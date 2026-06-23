package whatsmysurroundings.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import whatsmysurroundings.client.Commands.WMSCommands;
import whatsmysurroundings.client.Others.AutoQueryManager;
import whatsmysurroundings.client.Others.KeyBindings;
import whatsmysurroundings.client.Render.BlockRender;

@Environment(EnvType.CLIENT)
public class WhatsMySurroundingsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // 1. 注册玩家进入世界的事件处理器
        WorldJoinHandler.register();

        // 2. 注册按键绑定
        //KeyBindings.register();

        // 3. 注册按键监听器
        //KeyBindings.registerTickListener();

        // 4. 注册自定义命令
        WMSCommands.init();

        // 5. 注册方块高亮
        BlockRender.register(); 

        // 6. 注册自动查询
        AutoQueryManager.register();

        System.out.println("[WMS] 客户端初始化完成，已注册事件监听器");
    }
}