package whatsmysurroundings.client.Render;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

/**
 * 统一的高亮请求对象
 */
public class HighlightRequest {
    public final HighlightType type;
    public final HighlightMode mode;
    public final AABB boundingBox;  // 用于实体/任意形状
    public final float r, g, b, a;
    public int durationTicks; // -1 表示永久

    // 方块专用构造器（自动生成 AABB）
    public HighlightRequest(HighlightType type, HighlightMode mode, BlockPos pos,
                            float r, float g, float b, float a, int durationTicks) {
        this(type, mode,
             new AABB(pos.getX(), pos.getY(), pos.getZ(),
                      pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1),
             r, g, b, a, durationTicks);
    }

    // 通用构造器（支持自定义碰撞箱）
    public HighlightRequest(HighlightType type, HighlightMode mode, AABB boundingBox,
                            float r, float g, float b, float a, int durationTicks) {
        this.type = type;
        this.mode = mode;
        this.boundingBox = boundingBox;
        this.r = r;
        this.g = g;
        this.b = b;
        this.a = a;
        this.durationTicks = durationTicks;
    }
}