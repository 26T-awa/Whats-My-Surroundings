package whatsmysurroundings.client.Commands;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.concurrent.CompletableFuture;

// 这只是一个简单的全部方块注册名建议提供器，在未来应接入原版内置的 BlockArgument 类型
public class EntitySuggestionProvider implements SuggestionProvider<FabricClientCommandSource> {
    @Override
    public CompletableFuture<Suggestions> getSuggestions(CommandContext<FabricClientCommandSource> context,
            SuggestionsBuilder builder) {
        // 遍历游戏中所有已注册的方块
        for (var entityEntry : BuiltInRegistries.ENTITY_TYPE.entrySet()) {
            // 获取方块的注册ID，例如 "minecraft:stone"
            String entityId = String.valueOf(entityEntry.getKey().identifier());
            // 将ID作为建议添加到builder中
            builder.suggest(entityId);
        }
        // 构建并返回建议列表
        return builder.buildFuture();
    }
}