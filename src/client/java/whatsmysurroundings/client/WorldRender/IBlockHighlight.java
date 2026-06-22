package whatsmysurroundings.client.WorldRender;

import net.minecraft.core.BlockPos;

import java.util.List;

public interface IBlockHighlight {
    List<BlockPos> getHighlightedBlocks();
    void addHighlightedBlock(BlockPos pos);
    void clearHighlights();
}