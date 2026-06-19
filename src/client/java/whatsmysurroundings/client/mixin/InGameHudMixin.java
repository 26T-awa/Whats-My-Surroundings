package whatsmysurroundings.client.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

@Environment(EnvType.CLIENT)

public class InGameHudMixin {
    @Inject(
        method = "render",
        at = @At(value = "TAIL")
    )
    private void onRender() {
    }
}