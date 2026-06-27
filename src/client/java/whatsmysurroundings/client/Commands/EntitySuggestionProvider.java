package whatsmysurroundings.client.Commands;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.concurrent.CompletableFuture;

public class EntitySuggestionProvider implements SuggestionProvider<FabricClientCommandSource> {
    @Override
    public CompletableFuture<Suggestions> getSuggestions(CommandContext<FabricClientCommandSource> context,
            SuggestionsBuilder builder) {
        for (var entityEntry : BuiltInRegistries.ENTITY_TYPE.entrySet()) {
            String entityId = entityEntry.getValue().getDescriptionId();
            builder.suggest(getID(entityId));// This support "zombie"
            builder.suggest("minecraft:" + getID(entityId));// This "minecraft:zombie"
        }
        // 构建并返回建议列表
        return builder.buildFuture();
    }

    public static String getID(String raw) {// raw = "entity.minecraft.XXX", I want the "XXX"
        String[] parts = raw.split("\\.");
        return parts[parts.length - 1];// 这是一个非常聪明的代码awa(
    }
}