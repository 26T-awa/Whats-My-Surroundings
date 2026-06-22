package whatsmysurroundings.client.WorldRender;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

public class BlockHighlightStorage implements IBlockHighlight {
    private final List<BlockPos> highlightedBlocks = new ArrayList<>();

    @Override
    public List<BlockPos> getHighlightedBlocks() {
        return highlightedBlocks;
    }

    @Override
    public void addHighlightedBlock(BlockPos pos) {
        if (!highlightedBlocks.contains(pos)) {
            highlightedBlocks.add(pos);
        }
    }

    @Override
    public void clearHighlights() {
        highlightedBlocks.clear();
    }
}