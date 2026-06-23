package whatsmysurroundings.client.Commands;

import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

public class wmsentityCommand {
    private static int ENTITY_RADIUS = 5;// 方形半径
    private static List<Entity> TargetEntity = new ArrayList<>();

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(
                literal("wmsentity")
                    .then(literal("find")
                        .executes(wmsentityCommand::findEntities)
                    )
            );
        });
    }

    private static int findEntities(CommandContext<FabricClientCommandSource> context) {
        LocalPlayer player = context.getSource().getPlayer();

        BlockPos centerPos = player.blockPosition();
        // 定义一个以玩家为中心，半径 ENTITY_RADIUS 的包围盒
        AABB areaBox = AABB.ofSize(centerPos.getCenter(), ENTITY_RADIUS * 2, ENTITY_RADIUS * 2, ENTITY_RADIUS * 2);

        // 获取半径内的所有实体（不包括玩家自己）
        List<Entity> nearbyEntities = player.level().getEntitiesOfClass(Entity.class, areaBox,
                entity -> entity != player && entity.isAlive());

        if (nearbyEntities.isEmpty()) {
            context.getSource().sendFeedback(Component.literal("§e[WMS] 在半径 " + ENTITY_RADIUS + " 内未找到任何实体"));
            return 1;
        }

        // 按距离排序（近到远）
        nearbyEntities.sort(Comparator.comparingDouble(player::distanceTo));

        context.getSource().sendFeedback(Component.literal(
                String.format("§a[WMS] 找到 §e%d §a个实体:", nearbyEntities.size())
        ));

        // 输出每个实体的信息
        for (Entity entity : nearbyEntities) {
            String entityName = entity.getName().getString();
            double distance = player.distanceTo(entity);
            String healthInfo = "";
            if (entity instanceof LivingEntity living) {
                healthInfo = String.format(" §7❤ %.1f/%.1f", living.getHealth(), living.getMaxHealth());
            }

            String message = String.format(
                    "  §7[%d, %d, %d] §f%s§7 (%.1f m)%s",
                    (int) entity.getX(), (int) entity.getY(), (int) entity.getZ(),
                    entityName, distance, healthInfo
            );
            context.getSource().sendFeedback(Component.literal(message));
        }

        return 1;
    }
}