package whatsmysurroundings.client.Commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.synchronization.SuggestionProviders;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import whatsmysurroundings.client.Others.AutoQueryManager;
import whatsmysurroundings.client.Render.BlockRender;
import whatsmysurroundings.client.Render.HighlightMode;
import whatsmysurroundings.client.Render.HighlightType;

import java.util.*;
import java.util.stream.Collectors;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

public class wmsentityCommand {
    private static int radius = 10; // 查询半径
    private static int entityTaskId = -1; // 自动查询任务ID
    private static int internal = 20; // 默认自动查询间隔 (Tick)
    private static boolean enableAutoQuery = false;

    // 目标实体类型列表 (存储实体注册名, 如 "minecraft:zombie")
    private static final List<String> TargetEntities = new ArrayList<>();

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
        for (String name : TargetEntities) {
            builder.suggest(name);
        }
        return builder.buildFuture();
    };

    public static void register() {
        WMSCommands.addCommandString("wmsentity");

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(
                    ClientCommands.literal("wmsentity")
                            // /wmsentity 空指令
                            .executes(context -> nullCommand(context))

                            // /wmsentity set <radius>
                            .then(ClientCommands.literal("set")
                                    .then(ClientCommands.argument("radius", IntegerArgumentType.integer())
                                            .suggests(RADIUS_SUGGESTIONS)
                                            .executes(context -> setRadius(context,
                                                    IntegerArgumentType.getInteger(context, "radius"))))

                            )

                            // /wmsentity target add/remove/list
                            .then(ClientCommands.literal("target")
                                    // add <entity_id> - 添加目标实体类型
                                    .then(ClientCommands.literal("add")
                                            .then(ClientCommands.argument("entity_id", StringArgumentType.word())
                                                    .suggests(SuggestionProviders
                                                            .cast(SuggestionProviders.SUMMONABLE_ENTITIES))
                                                    .executes(context -> addTargetEntity(context,
                                                            StringArgumentType.getString(context, "entity_id"))))

                                    )

                                    // remove <entity_id>
                                    .then(ClientCommands.literal("remove")
                                            .then(ClientCommands.argument("entity_id", StringArgumentType.word())
                                                    .suggests(TARGET_ENTITY_SUGGESTIONS)
                                                    .executes(context -> removeTargetEntity(context,
                                                            StringArgumentType.getString(context, "entity_id"))))

                                    )

                                    // list
                                    .then(ClientCommands.literal("list")
                                            .executes(context -> showTargetEntities(context))

                                    )

                            )

                            // /wmsentity find
                            .then(ClientCommands.literal("find")
                                    .executes(context -> findEntitiesOnly(context))
                                    // /wmsentity find *
                                    .then(ClientCommands.literal("*")
                                            .executes(context -> findAllEntities(context)))
                                    // /wmsentity find <entity_id> (查询时临时添加)

                                    .then(ClientCommands.argument("entity_id", StringArgumentType.word())
                                            .suggests(SuggestionProviders
                                                    .cast(SuggestionProviders.SUMMONABLE_ENTITIES))
                                            .executes(context -> {
                                                String entityId = StringArgumentType.getString(context, "entity_id");
                                                return findAndAddTargetEntity(context, entityId);
                                            }))

                            )

                            // /wmsentity clear
                            .then(ClientCommands.literal("clear")
                                    .executes(context -> clearEntityHighlight(context))

                            )

                            // /wmsentity auto
                            .then(ClientCommands.literal("auto")
                                    .executes(context -> toggleAutoFind(context))
                                    .then(ClientCommands.argument("internal", IntegerArgumentType.integer())
                                            .executes(context -> setInternal(context,
                                                    IntegerArgumentType.getInteger(context, "internal"))))

                            )

            );
        });
    }

    // 查询并高亮所有目标实体 (仅显示目标实体)
    private static int findEntitiesOnly(CommandContext<FabricClientCommandSource> context) {
        if (TargetEntities.isEmpty()) {
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
            BlockRender.addHighlight(HighlightType.ENTITY, HighlightMode.MANUAL, box,
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
     * 查询所有实体 (目标实体高亮显示)
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
                BlockRender.addHighlight(HighlightType.ENTITY, HighlightMode.MANUAL,
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

    // ======================== 目标实体管理 ========================

    private static int addTargetEntity(CommandContext<FabricClientCommandSource> context, String entityId) {
        // 检查是否已存在
        if (TargetEntities.contains(entityId)) {
            context.getSource().sendFeedback(
                    Component.literal(String.format("§c[WMS 错误] 已存在目标实体类型: %s", entityId)));
            return 0;
        }

        TargetEntities.add(entityId);
        context.getSource().sendFeedback(
                Component.literal(String.format("§a[WMS] 增加目标实体类型: %s", entityId)));
        return 1;
    }

    private static int removeTargetEntity(CommandContext<FabricClientCommandSource> context, String entityId) {
        if (TargetEntities.remove(entityId)) {
            context.getSource().sendFeedback(
                    Component.literal(String.format("§e[WMS] 已移除目标实体类型: §m%s§r", entityId)));
            return 1;
        } else {
            context.getSource().sendFeedback(
                    Component.literal(String.format("§c[WMS 错误] 未找到目标实体类型: %s", entityId)));
            return 0;
        }
    }

    private static int showTargetEntities(CommandContext<FabricClientCommandSource> context) {
        int size = TargetEntities.size();
        context.getSource().sendFeedback(
                Component.literal(String.format("§a[WMS] 已有的目标实体类型: %d个", size)));

        if (size == 0)
            return 0;

        for (int i = 0; i < size; i++) {
            String suffix = (i == size - 1) ? "." : ",";
            context.getSource().sendFeedback(
                    Component.literal(String.format("§7  %2d : §f%s §7%s", i + 1, TargetEntities.get(i), suffix)));
        }
        return size;
    }

    /**
     * 临时添加并查询单个实体
     */
    private static int findAndAddTargetEntity(CommandContext<FabricClientCommandSource> context, String entityId) {
        boolean existed = TargetEntities.contains(entityId);
        if (!existed) {
            TargetEntities.add(entityId);
        }

        int result = findEntitiesOnly(context);

        if (!existed) {
            TargetEntities.remove(entityId);
        }
        return result;
    }

    // ======================== 辅助方法 ========================

    private static boolean isTargetEntity(Entity entity) {
        if (TargetEntities.isEmpty())
            return false;
        String registryName = entity.getType().getDescriptionId();
        return TargetEntities.contains(registryName);
    }

    // ======================== 自动查询 ========================

    private static void checkState(CommandContext<FabricClientCommandSource> context) {
        if (enableAutoQuery) {
            if (entityTaskId != -1) {
                AutoQueryManager.removeTask(entityTaskId);
                entityTaskId = -1;
            }

            entityTaskId = AutoQueryManager.addTask(
                    () -> {
                        // 自动查询使用独立的颜色 (紫色半透明)

                        LocalPlayer player = Minecraft.getInstance().player;
                        if (player == null)
                            return;

                        if (TargetEntities.isEmpty()) {
                            player.sendSystemMessage(Component.literal("§e[WMS 自动] 请先添加目标实体类型"));
                            toggleAutoFind(context);
                            return;
                        }

                        Level world = player.level();
                        BlockPos centerPos = player.blockPosition();
                        AABB areaBox = AABB.ofSize(centerPos.getCenter(), radius * 2, radius * 2, radius * 2);

                        List<Entity> entities = world.getEntitiesOfClass(Entity.class, areaBox,
                                e -> e != player && e.isAlive() && isTargetEntity(e));

                        // 清除旧的自动高亮
                        BlockRender.clearHighlights(HighlightType.ENTITY, HighlightMode.AUTO);

                        for (Entity entity : entities) {
                            BlockRender.addHighlight(HighlightType.ENTITY, HighlightMode.AUTO,
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
            BlockRender.clearHighlights(HighlightType.ENTITY, HighlightMode.AUTO);
            context.getSource().sendFeedback(
                    Component.literal("§e[WMS] 实体自动查询已关闭"));
        }
    }

    private static int toggleAutoFind(CommandContext<FabricClientCommandSource> context) {
        enableAutoQuery = !enableAutoQuery;
        checkState(context);
        return 1;
    }

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

    // ======================== 其他命令 ========================

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

    private static int clearEntityHighlight(CommandContext<FabricClientCommandSource> context) {
        BlockRender.clearHighlights(HighlightType.ENTITY, HighlightMode.MANUAL);
        BlockRender.clearHighlights(HighlightType.ENTITY, HighlightMode.AUTO);
        context.getSource().sendFeedback(
                Component.literal("§a[WMS] 已清除所有实体高亮"));
        return 1;
    }

    private static int nullCommand(CommandContext<FabricClientCommandSource> context) {
        context.getSource().sendFeedback(
                Component.literal("§7[WMS] /wmsentity - 实体查询命令"));
        context.getSource().sendFeedback(
                Component.literal("§7  子命令: set, target, find, clear, auto"));
        return 1;
    }
}