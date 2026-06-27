package whatsmysurroundings.client.Commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.commands.synchronization.SuggestionProviders;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import whatsmysurroundings.client.Others.AutoQueryManager;
import whatsmysurroundings.client.Render.HlightRender;
import whatsmysurroundings.client.Render.HighlightMode;
import whatsmysurroundings.client.Render.HighlightType;

import java.util.*;
import java.util.stream.Collectors;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

public class wmsentityCommand {
    private static int radius = 10; // 方形半径
    private static int entityTaskId = -1; // 自动查询任务ID
    private static int internal = 100; // 默认自动查询间隔 (Tick)
    private static boolean enableAutoQuery = false;
    private static final List<EntityType<?>> TargetEntityTypes = new ArrayList<>();

    // 半径建议
    private static final SuggestionProvider<FabricClientCommandSource> RADIUS_SUGGESTIONS = (context, builder) -> {
        List<Integer> suggestions = Arrays.asList(5, 10, 16, 32);
        for (int value : suggestions) {
            builder.suggest(String.valueOf(value));
        }
        return builder.buildFuture();
    };

    // 已有目标实体建议
    private static final SuggestionProvider<FabricClientCommandSource> TARGET_ENTITY_SUGGESTIONS = (context,
            builder) -> {
        for (EntityType<?> entityType : TargetEntityTypes) {
            builder.suggest(EntityTypeToId(entityType));
        }
        return builder.buildFuture();
    };

    public static void register() {
        WMSCommands.addCommandString("wmsentity");

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(
                    ClientCommands.literal("wmsentity")
                            // '/wmsentity' 直接输入无结果，返回值为1
                            .executes(context -> nullCommand(context))

                            // '/wmsentity set <radius>' 设置查询方形半径
                            .then(ClientCommands.literal("set")
                                    .then(ClientCommands.argument("radius", IntegerArgumentType.integer())
                                            .suggests(RADIUS_SUGGESTIONS)
                                            .executes(context -> setRadius(context,
                                                    IntegerArgumentType.getInteger(context, "radius")))))

                            // '/wmsentity target ...'
                            .then(ClientCommands.literal("target")
                                    // '/wmsentity target add <entity_id>' 添加目标实体类型
                                    .then(ClientCommands.literal("add")
                                            .then(ClientCommands
                                                    .argument("entity_id", StringArgumentType.greedyString())
                                                    .suggests(new EntitySuggestionProvider())
                                                    .executes(context -> addTargetEntityType(context,
                                                            StringToEntityType(
                                                                    StringArgumentType.getString(context,
                                                                            "entity_id"))))))

                                    // '/wmsentity target remove <entity_id>' 删除目标实体类型
                                    .then(ClientCommands.literal("remove")
                                            .then(ClientCommands
                                                    .argument("entity_id", StringArgumentType.greedyString())
                                                    .suggests(TARGET_ENTITY_SUGGESTIONS)
                                                    .executes(context -> removeTargetEntity(context, StringToEntityType(
                                                            StringArgumentType.getString(context, "entity_id"))))))

                                    // '/wmsentity target list' 列出已有的目标实体类型
                                    .then(ClientCommands.literal("list")
                                            .executes(context -> showTargetEntities(context))))

                            // '/wmsentity find' 查询仅目标实体类型
                            .then(ClientCommands.literal("find")
                                    .executes(context -> findEntitiesOnly(context))

                                    // '/wmsentity find *' 查询所有实体
                                    .then(ClientCommands.literal("*")
                                            .executes(context -> findAllEntities(context)))
                                    // /wmsentity find <entity_id> (查询时临时添加)

                                    .then(ClientCommands.argument("entity_id", StringArgumentType.greedyString())
                                            .suggests(SuggestionProviders
                                                    .cast(SuggestionProviders.SUMMONABLE_ENTITIES))
                                            .executes(context -> {
                                                return findAndaddTargetEntityType(context, StringToEntityType(
                                                        StringArgumentType.getString(context, "entity_id")));
                                            })))

                            // '/wmsentity clear' 清除显示
                            .then(ClientCommands.literal("clear")
                                    .executes(context -> clearEntityHighlight(context)))

                            // '/wmsentity auto' 切换自动查询
                            .then(ClientCommands.literal("auto")
                                    .executes(context -> toggleAutoFind(context))
                                    .then(ClientCommands.argument("internal", IntegerArgumentType.integer())
                                            .executes(context -> setInternal(context,
                                                    IntegerArgumentType.getInteger(context, "internal"))))));
        });
    }

    /**
     * 加入或删除任务（根据状态判断）
     * 
     * @param context 命令上下文
     */
    private static void checkState(CommandContext<FabricClientCommandSource> context) {
        if (enableAutoQuery) {
            if (entityTaskId != -1) {
                AutoQueryManager.removeTask(entityTaskId);
                entityTaskId = -1;
            }

            entityTaskId = AutoQueryManager.addTask(
                    () -> {
                        LocalPlayer player = Minecraft.getInstance().player;
                        if (player == null)
                            return;

                        if (TargetEntityTypes.isEmpty()) {
                            player.sendSystemMessage(Component.literal("§e[WMS 自动] 请先添加目标实体类型"));
                            toggleAutoFind(context);
                            return;
                        }

                        Level world = player.level();
                        BlockPos centerPos = player.blockPosition();
                        AABB areaBox = AABB.ofSize(centerPos.getCenter(), radius * 2, radius * 2, radius * 2);

                        List<Entity> entities = world.getEntitiesOfClass(Entity.class, areaBox,
                                e -> e != player && e.isAlive() && isTargetEntity(e));

                        for (Entity entity : entities) {
                            HlightRender.addHighlight(HighlightType.ENTITY, HighlightMode.AUTO,
                                    entity.getBoundingBox(), 0.7f, 0.3f, 0.8f, 0.5f, internal);
                        }

                        if (!entities.isEmpty() && internal > 20) {
                            player.sendSystemMessage(Component.literal(
                                    String.format("§7[WMS 自动] 找到 §e%d §7个目标实体", entities.size())));
                        }
                    },
                    internal);

            context.getSource().sendFeedback(
                    Component.literal("§e[WMS] 实体自动查询已开启，间隔 " + internal / 20 + " s(" + internal + ")"));
            if (internal <= 20)
                context.getSource().sendFeedback(
                        Component.literal("§e[WMS 警告] 间隔过短不会提示查询信息，仅显示实体高亮。"));

        } else {
            if (entityTaskId != -1) {
                AutoQueryManager.removeTask(entityTaskId);
                entityTaskId = -1;
            }
            HlightRender.clearHighlights(HighlightType.ENTITY, HighlightMode.AUTO);
            context.getSource().sendFeedback(
                    Component.literal("§e[WMS] 实体自动查询已关闭"));
        }
    }

    /**
     * 切换自动查询
     * 
     * @param context 命令上下文
     * @return 1
     */
    private static int toggleAutoFind(CommandContext<FabricClientCommandSource> context) {
        enableAutoQuery = !enableAutoQuery;
        checkState(context);
        return 1;
    }

    /**
     * 加入并查询目标实体种类
     * 
     * @param context        命令上下文
     * @param new_entityType 临时实体种类
     * @return 查询到的目标实体总数
     */
    private static int findAndaddTargetEntityType(CommandContext<FabricClientCommandSource> context,
            EntityType<?> new_entityType) {
        boolean existed = TargetEntityTypes.contains(new_entityType);
        if (!existed) {
            TargetEntityTypes.add(new_entityType);
        }

        int result = findEntitiesOnly(context);

        if (!existed) {
            TargetEntityTypes.remove(new_entityType);
        }
        return result;
    }

    /**
     * 查询仅目标实体种类
     * 
     * @param context 命令上下文
     * @return 查询到的目标实体总数
     */
    private static int findEntitiesOnly(CommandContext<FabricClientCommandSource> context) {
        if (TargetEntityTypes.isEmpty()) {
            context.getSource().sendFeedback(
                    Component.literal("§e[WMS] 请先使用 /wmsentity target add 添加目标实体类型"));
            return 0;
        }

        LocalPlayer player = context.getSource().getPlayer();

        Level world = player.level();
        BlockPos centerPos = player.blockPosition();

        // 构建搜索范围
        AABB areaBox = AABB.ofSize(centerPos.getCenter(), radius * 2, radius * 2, radius * 2);

        // 获取半径内所有实体
        List<Entity> nearbyEntities = world.getEntitiesOfClass(Entity.class, areaBox,
                e -> e != player && e.isAlive() && isTargetEntity(e));

        if (nearbyEntities.isEmpty()) {
            context.getSource().sendFeedback(
                    Component.literal("§e[WMS] 在半径 " + radius + " 内未找到任何目标实体"));
            return 0;
        }

        // 按距离排序
        nearbyEntities.sort(Comparator.comparingDouble(player::distanceToSqr));

        // 输出结果
        context.getSource().sendFeedback(
                Component.literal(String.format("§a[WMS] 在半径 %d 内找到 §e%d §a个目标实体:", radius, nearbyEntities.size())));

        for (Entity entity : nearbyEntities) {
            String entityName = entity.getType().getDescription().getString();
            String registryName = entity.getType().getDescriptionId();
            double distance = player.distanceTo(entity);

            // 高亮实体 (使用实体碰撞箱)
            AABB box = entity.getBoundingBox();
            HlightRender.addHighlight(HighlightType.ENTITY, HighlightMode.MANUAL, box,
                    0.0f, 1.0f, 0.0f, 0.7f, -1);

            // 构建输出文本
            String healthInfo = "";
            if (entity instanceof LivingEntity living) {
                healthInfo = String.format(" §7❤ %.1f/%.1f", living.getHealth(), living.getMaxHealth());
            }

            context.getSource().sendFeedback(
                    Component.literal(String.format(
                            "  §7[%.1f, %.1f, %.1f] §f%s§7 (%s)§7 %.1f m%s",
                            entity.getX(), entity.getY(), entity.getZ(),
                            entityName, registryName, distance, healthInfo)));
        }

        return nearbyEntities.size();
    }

    /**
     * 查询所有实体
     * 
     * @param context 命令上下文
     * @return 查询到的实体总数
     */
    private static int findAllEntities(CommandContext<FabricClientCommandSource> context) {
        LocalPlayer player = context.getSource().getPlayer();

        Level world = player.level();
        BlockPos centerPos = player.blockPosition();

        AABB areaBox = AABB.ofSize(centerPos.getCenter(), radius * 2, radius * 2, radius * 2);

        List<Entity> nearbyEntities = world.getEntitiesOfClass(Entity.class, areaBox,
                e -> e != player && e.isAlive());

        if (nearbyEntities.isEmpty()) {
            context.getSource().sendFeedback(
                    Component.literal("§e[WMS] 在半径 " + radius + " 内未找到任何实体"));
            return 0;
        }

        nearbyEntities.sort(Comparator.comparingDouble(player::distanceToSqr));

        int targetCount = 0;
        for (Entity entity : nearbyEntities) {
            if (isTargetEntity(entity))
                targetCount++;
        }

        context.getSource().sendFeedback(
                Component.literal(String.format(
                        "§a[WMS] 半径 %d 内共有 §e%d §a个实体，其中目标实体 §e%d §a个",
                        radius, nearbyEntities.size(), targetCount)));

        // 显示实体列表 (限制显示数量)
        int maxDisplay = 20;
        int displayed = 0;
        boolean hasMore = false;
        StringBuilder text = new StringBuilder();

        for (Entity entity : nearbyEntities) {
            if (displayed >= maxDisplay) {
                hasMore = true;
                break;
            }

            boolean isTarget = isTargetEntity(entity);
            String prefix = isTarget ? "\n§a  * " : "\n§7    ";
            String name = entity.getType().getDescription().getString();

            // 高亮目标实体
            if (isTarget) {
                HlightRender.addHighlight(HighlightType.ENTITY, HighlightMode.MANUAL,
                        entity.getBoundingBox(), 0.0f, 1.0f, 0.0f, 0.7f, -1);
            }

            text.append(String.format("%s[%.1f, %.1f, %.1f] §f%s",
                    prefix, entity.getX(), entity.getY(), entity.getZ(), name));

            if (entity instanceof LivingEntity living) {
                text.append(String.format(" §7❤%.1f", living.getHealth()));
            }

            displayed++;
        }

        context.getSource().sendFeedback(Component.literal(text.toString()));

        if (hasMore) {
            context.getSource().sendFeedback(
                    Component.literal(String.format("§e[WMS] ... 还有 %d 个实体未显示",
                            nearbyEntities.size() - maxDisplay)));
        }

        return nearbyEntities.size();
    }

    /**
     * 添加目标实体种类
     * 
     * @param context        命令上下文
     * @param new_entityType 新增的实体种类
     * @return 1成功；0失败
     */
    private static int addTargetEntityType(CommandContext<FabricClientCommandSource> context,
            EntityType<?> new_entityType) {
        // 检查是否已存在
        if (TargetEntityTypes.contains(new_entityType)) {
            context.getSource().sendFeedback(
                    Component.literal(String.format("§c[WMS 错误] 已存在目标实体类型: %s", EntityTypeToText(new_entityType))));
            return 0;
        }

        TargetEntityTypes.add(new_entityType);
        context.getSource().sendFeedback(
                Component.literal(String.format("§a[WMS] 增加目标实体类型: %s", EntityTypeToText(new_entityType))));
        return 1;
    }

    /**
     * 列出已有的目标实体种类
     * 
     * @param context 命令上下文
     * @return 1成功；0失败
     */
    private static int showTargetEntities(CommandContext<FabricClientCommandSource> context) {
        int size = TargetEntityTypes.size();
        context.getSource().sendFeedback(
                Component.literal(String.format("§a[WMS] 已有的目标实体类型: %d个", size)));

        if (size == 0)
            return 0;

        for (int i = 0; i < size; i++) {
            String suffix = (i == size - 1) ? "." : ",";
            context.getSource().sendFeedback(
                    Component.literal(String.format("§7  %2d : §f%s §7%s", i + 1,
                            TargetEntityTypes.get(i).getDescriptionId(), suffix)));
        }
        return size;
    }

    /**
     * 删除目标实体种类
     * 
     * @param context          命令上下文
     * @param tormv_entityType 待删除的实体种类
     * @return 1成功；0失败
     */
    private static int removeTargetEntity(CommandContext<FabricClientCommandSource> context,
            EntityType<?> tormv_entityType) {
        if (TargetEntityTypes.remove(tormv_entityType)) {
            context.getSource().sendFeedback(
                    Component.literal(String.format("§e[WMS] 已移除目标实体类型: §m%s§r", EntityTypeToText(tormv_entityType))));
            return 1;
        } else {
            context.getSource().sendFeedback(
                    Component.literal(String.format("§c[WMS 错误] 未找到目标实体类型: %s", EntityTypeToText(tormv_entityType))));
            return 0;
        }
    }

    /**
     * 设置方形半径
     * 
     * @param context   命令上下文
     * @param newRadius 新方形半径
     * @return 1成功；0失败
     */
    private static int setRadius(CommandContext<FabricClientCommandSource> context, int newRadius) {
        if (newRadius < 0) {
            context.getSource().sendFeedback(
                    Component.literal("§c[WMS 错误] 半径不能小于0"));
            return 0;
        }
        radius = Math.min(newRadius, 64); // 限制最大半径64
        context.getSource().sendFeedback(
                Component.literal(String.format("§a[WMS] 实体查询半径设为 %d", radius)));
        return radius;
    }

    /**
     * 设置间隔
     * 
     * @param context     命令上下文
     * @param newInternal 新间隔
     * @return 1成功；0失败
     */
    private static int setInternal(CommandContext<FabricClientCommandSource> context, int newInternal) {
        if (newInternal < 0) {
            context.getSource().sendFeedback(
                    Component.literal("§c[WMS 错误] 间隔不能小于0"));
            return 0;
        }
        internal = newInternal;
        if (internal < 20) {
            context.getSource().sendFeedback(
                    Component.literal("§e[WMS 警告] 间隔小于20 Tick 可能造成卡顿"));
        }
        context.getSource().sendFeedback(
                Component.literal(String.format("§a[WMS] 实体自动查询间隔设为 %d Tick", internal)));

        enableAutoQuery = true;
        checkState(context);
        return internal;
    }

    /**
     * 清除实体高亮
     * 
     * @param context 命令上下文
     * @return 1成功；0失败
     */
    private static int clearEntityHighlight(CommandContext<FabricClientCommandSource> context) {
        HlightRender.clearHighlights(HighlightType.ENTITY, HighlightMode.MANUAL);
        HlightRender.clearHighlights(HighlightType.ENTITY, HighlightMode.AUTO);
        context.getSource().sendFeedback(
                Component.literal("§a[WMS] 已清除所有实体高亮"));
        return 1;
    }

    /**
     * 空指令
     * 
     * @param context 命令上下文
     * @return 1
     */
    private static int nullCommand(CommandContext<FabricClientCommandSource> context) {
        return 1;
    }

    /**
     * 实体ID转实体种类
     * 
     * @param entityId 实体ID "zombie"或 "minecraft:zombie"
     * @return 实体ID对应的实体种类
     */
    private static EntityType<?> StringToEntityType(String entityId) {
        Identifier identifier = Identifier.tryParse(entityId);
        if (identifier == null)
            return null;

        Optional<Holder.Reference<EntityType<?>>> optionalHolder = BuiltInRegistries.ENTITY_TYPE.get(identifier);

        if (optionalHolder.isEmpty())
            return null;

        EntityType<?> entityType = optionalHolder.get().value();

        return entityType;
    }

    /**
     * 实体种类转ID
     * 
     * @param entityType 实体种类
     * @return 返回"zombie"，而不是"minecraft:zombie"
     */
    private static String EntityTypeToId(EntityType<?> entityType) {
        return EntitySuggestionProvider.getID(entityType.getDescriptionId());
    };

    /**
     * 实体种类转译名
     * 
     * @param entityType 实体种类
     * @return 返回译名"僵尸"，而不是"minecraft:zombie"
     */
    private static String EntityTypeToText(EntityType<?> entityType) {
        return entityType.getDescription().getString();
    };

    /**
     * 判断该实体是不是目标实体
     * 
     * @param entity 实体
     * @return true是；false否
     */
    private static boolean isTargetEntity(Entity entity) {
        if (TargetEntityTypes.isEmpty())
            return false;
        EntityType<?> entityType = entity.getType();
        return TargetEntityTypes.contains(entityType);
    }
}