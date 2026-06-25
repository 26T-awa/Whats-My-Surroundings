package whatsmysurroundings.client.Render;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.concurrent.CopyOnWriteArrayList;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import whatsmysurroundings.WhatsMySurroundings;
import whatsmysurroundings.client.Render.HighlightMode;
import whatsmysurroundings.client.Render.HighlightRequest;
import whatsmysurroundings.client.Render.HighlightType;

/**
 * 统一高亮渲染引擎
 * 支持任意类型（方块/实体/未来扩展），每种类型独立管理手动/自动模式
 */
public class BlockRender {
    private static final RenderPipeline FILLED_THROUGH_WALLS = RenderPipelines
            .register(RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath(WhatsMySurroundings.MOD_ID,
                            "pipeline/debug_filled_box_through_walls"))
                    .withDepthStencilState(Optional.empty())
                    .build());

    // ========== 统一数据存储 ==========
    private static final List<HighlightRequest> highlights = new CopyOnWriteArrayList<>();

    // ========== 渲染资源 ==========
    private static final ByteBufferBuilder ALLOCATOR = new ByteBufferBuilder(RenderType.SMALL_BUFFER_SIZE);
    private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
    private static final Vector3f MODEL_OFFSET = new Vector3f();
    private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();
    private BufferBuilder buffer;
    private MappableRingBuffer vertexBuffer;

    private static BlockRender instance;

    // ========== 公共 API ==========

    /**
     * 在客户端初始化时注册渲染事件
     */
    public static void register() {
        if (instance == null) {
            instance = new BlockRender();
        }
        LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(instance::renderAllHighlights);
        System.out.println("[WMS] BlockRender 已注册渲染事件");
    }

    /**
     * 添加高亮（方块位置）
     */
    public static void addHighlight(HighlightType type, HighlightMode mode, BlockPos pos,
                                    float r, float g, float b, float a, int durationTicks) {
        synchronized (highlights) {
            highlights.add(new HighlightRequest(type, mode, pos, r, g, b, a, durationTicks));
        }
    }

    /**
     * 添加高亮（自定义碰撞箱，用于实体等）
     */
    public static void addHighlight(HighlightType type, HighlightMode mode, AABB box,
                                    float r, float g, float b, float a, int durationTicks) {
        synchronized (highlights) {
            highlights.add(new HighlightRequest(type, mode, box, r, g, b, a, durationTicks));
        }
    }

    /**
     * 清除指定类型和模式的所有高亮
     */
    public static void clearHighlights(HighlightType type, HighlightMode mode) {
        synchronized (highlights) {
            highlights.removeIf(req -> req.type == type && req.mode == mode);
        }
    }

    /**
     * 清除指定类型的所有高亮（同时清除手动和自动）
     */
    public static void clearHighlights(HighlightType type) {
        synchronized (highlights) {
            highlights.removeIf(req -> req.type == type);
        }
    }

    /**
     * 清除所有高亮
     */
    public static void clearAll() {
        synchronized (highlights) {
            highlights.clear();
        }
    }

    /**
     * 清除指定位置的高亮（不限类型和模式）
     */
    public static void removeAt(BlockPos pos) {
        synchronized (highlights) {
            highlights.removeIf(req -> {
                AABB box = req.boundingBox;
                return box.minX == pos.getX() && box.minY == pos.getY() && box.minZ == pos.getZ()
                        && box.maxX == pos.getX() + 1 && box.maxY == pos.getY() + 1 && box.maxZ == pos.getZ() + 1;
            });
        }
    }

    /**
     * 获取当前高亮总数（用于调试）
     */
    public static int getCount() {
        synchronized (highlights) {
            return highlights.size();
        }
    }

    // ========== Tick 更新（清理过期高亮） ==========

    public static void tick() {
        synchronized (highlights) {
            highlights.removeIf(req -> req.durationTicks >= 0 && (req.durationTicks--) <= 0);
        }
    }

    // ========== 渲染核心 ==========

    private void renderAllHighlights(LevelRenderContext context) {
        if (highlights.isEmpty()) {
            if (this.buffer != null) {
                this.buffer = null;
            }
            return;
        }

        PoseStack matrices = context.poseStack();
        Vec3 camera = context.levelState().cameraRenderState.pos;

        matrices.pushPose();
        matrices.translate(-camera.x, -camera.y, -camera.z);

        if (this.buffer == null) {
            this.buffer = new BufferBuilder(ALLOCATOR,
                    FILLED_THROUGH_WALLS.getVertexFormatMode(),
                    FILLED_THROUGH_WALLS.getVertexFormat());
        }

        // 遍历所有高亮请求
        for (HighlightRequest req : highlights) {
            renderFilledBox(matrices.last().pose(), this.buffer,
                    (float) req.boundingBox.minX, (float) req.boundingBox.minY, (float) req.boundingBox.minZ,
                    (float) req.boundingBox.maxX, (float) req.boundingBox.maxY, (float) req.boundingBox.maxZ,
                    req.r, req.g, req.b, req.a);
        }

        matrices.popPose();
        drawFilledThroughWalls(Minecraft.getInstance(), FILLED_THROUGH_WALLS);
    }

    // ========== 底层渲染方法（保持不变） ==========

    private void renderFilledBox(Matrix4fc positionMatrix, BufferBuilder buffer,
                                  float minX, float minY, float minZ,
                                  float maxX, float maxY, float maxZ,
                                  float red, float green, float blue, float alpha) {
        // Front Face
        buffer.addVertex(positionMatrix, minX, minY, maxZ).setColor(red, green, blue, alpha);
        buffer.addVertex(positionMatrix, maxX, minY, maxZ).setColor(red, green, blue, alpha);
        buffer.addVertex(positionMatrix, maxX, maxY, maxZ).setColor(red, green, blue, alpha);
        buffer.addVertex(positionMatrix, minX, maxY, maxZ).setColor(red, green, blue, alpha);

        // Back face
        buffer.addVertex(positionMatrix, maxX, minY, minZ).setColor(red, green, blue, alpha);
        buffer.addVertex(positionMatrix, minX, minY, minZ).setColor(red, green, blue, alpha);
        buffer.addVertex(positionMatrix, minX, maxY, minZ).setColor(red, green, blue, alpha);
        buffer.addVertex(positionMatrix, maxX, maxY, minZ).setColor(red, green, blue, alpha);

        // Left face
        buffer.addVertex(positionMatrix, minX, minY, minZ).setColor(red, green, blue, alpha);
        buffer.addVertex(positionMatrix, minX, minY, maxZ).setColor(red, green, blue, alpha);
        buffer.addVertex(positionMatrix, minX, maxY, maxZ).setColor(red, green, blue, alpha);
        buffer.addVertex(positionMatrix, minX, maxY, minZ).setColor(red, green, blue, alpha);

        // Right face
        buffer.addVertex(positionMatrix, maxX, minY, maxZ).setColor(red, green, blue, alpha);
        buffer.addVertex(positionMatrix, maxX, minY, minZ).setColor(red, green, blue, alpha);
        buffer.addVertex(positionMatrix, maxX, maxY, minZ).setColor(red, green, blue, alpha);
        buffer.addVertex(positionMatrix, maxX, maxY, maxZ).setColor(red, green, blue, alpha);

        // Top face
        buffer.addVertex(positionMatrix, minX, maxY, maxZ).setColor(red, green, blue, alpha);
        buffer.addVertex(positionMatrix, maxX, maxY, maxZ).setColor(red, green, blue, alpha);
        buffer.addVertex(positionMatrix, maxX, maxY, minZ).setColor(red, green, blue, alpha);
        buffer.addVertex(positionMatrix, minX, maxY, minZ).setColor(red, green, blue, alpha);

        // Bottom face
        buffer.addVertex(positionMatrix, minX, minY, minZ).setColor(red, green, blue, alpha);
        buffer.addVertex(positionMatrix, maxX, minY, minZ).setColor(red, green, blue, alpha);
        buffer.addVertex(positionMatrix, maxX, minY, maxZ).setColor(red, green, blue, alpha);
        buffer.addVertex(positionMatrix, minX, minY, maxZ).setColor(red, green, blue, alpha);
    }

    private void drawFilledThroughWalls(Minecraft client, RenderPipeline pipeline) {
        // Build the buffer
        MeshData builtBuffer = this.buffer.buildOrThrow();
        MeshData.DrawState drawParameters = builtBuffer.drawState();
        VertexFormat format = drawParameters.format();

        GpuBuffer vertices = this.upload(drawParameters, format, builtBuffer);

        draw(client, pipeline, builtBuffer, drawParameters, vertices, format);

        // Rotate the vertex buffer so we are less likely to use buffers that the GPU is
        // using
        this.vertexBuffer.rotate();
        this.buffer = null;
    }

    private GpuBuffer upload(MeshData.DrawState drawParameters, VertexFormat format, MeshData builtBuffer) {
        // Calculate the size needed for the vertex buffer
        int vertexBufferSize = drawParameters.vertexCount() * format.getVertexSize();

        // Initialize or resize the vertex buffer as needed
        if (this.vertexBuffer == null || this.vertexBuffer.size() < vertexBufferSize) {
            if (this.vertexBuffer != null) {
                this.vertexBuffer.close();
            }

            this.vertexBuffer = new MappableRingBuffer(() -> WhatsMySurroundings.MOD_ID + " example render pipeline",
                    GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE, vertexBufferSize);
        }

        // Copy vertex data into the vertex buffer
        CommandEncoder commandEncoder = RenderSystem.getDevice().createCommandEncoder();

        try (GpuBuffer.MappedView mappedView = commandEncoder.mapBuffer(
                this.vertexBuffer.currentBuffer().slice(0, builtBuffer.vertexBuffer().remaining()), false, true)) {
            MemoryUtil.memCopy(builtBuffer.vertexBuffer(), mappedView.data());
        }

        return this.vertexBuffer.currentBuffer();
    }

    private static void draw(Minecraft client, RenderPipeline pipeline, MeshData builtBuffer,
                              MeshData.DrawState drawParameters, GpuBuffer vertices, VertexFormat format) {
        GpuBuffer indices;
        VertexFormat.IndexType indexType;

        if (pipeline.getVertexFormatMode() == VertexFormat.Mode.QUADS) {
            // Sort the quads if there is translucency
            builtBuffer.sortQuads(ALLOCATOR, RenderSystem.getProjectionType().vertexSorting());
            // Upload the index buffer
            indices = pipeline.getVertexFormat().uploadImmediateIndexBuffer(builtBuffer.indexBuffer());
            indexType = builtBuffer.drawState().indexType();
        } else {
            // Use the general shape index buffer for non-quad draw modes
            RenderSystem.AutoStorageIndexBuffer shapeIndexBuffer = RenderSystem
                    .getSequentialBuffer(pipeline.getVertexFormatMode());
            indices = shapeIndexBuffer.getBuffer(drawParameters.indexCount());
            indexType = shapeIndexBuffer.type();
        }

        // Actually execute the draw
        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
                .writeTransform(RenderSystem.getModelViewMatrix(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);
        try (RenderPass renderPass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(() -> WhatsMySurroundings.MOD_ID + " example render pipeline rendering",
                        client.getMainRenderTarget().getColorTextureView(), OptionalInt.empty(),
                        client.getMainRenderTarget().getDepthTextureView(), OptionalDouble.empty())) {
            renderPass.setPipeline(pipeline);

            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", dynamicTransforms);

            // Bind texture if applicable:
            // Sampler0 is used for texture inputs in vertices
            // renderPass.bindTexture("Sampler0", textureSetup.texure0(),
            // textureSetup.sampler0());

            renderPass.setVertexBuffer(0, vertices);
            renderPass.setIndexBuffer(indices, indexType);

            // The base vertex is the starting index when we copied the data into the vertex
            // buffer divided by vertex size
            // noinspection ConstantValue
            renderPass.drawIndexed(0 / format.getVertexSize(), 0, drawParameters.indexCount(), 1);
        }

        builtBuffer.close();
    }
}