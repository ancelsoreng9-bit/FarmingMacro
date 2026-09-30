package melonmacro.client.mixin;

import melonmacro.client.MelonMacroClient;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {

    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void melonmacro$cancelTurnWhileLocked(CallbackInfo ci) {
        if (MelonMacroClient.isMacroEnabled()) {
            ci.cancel();
        }
    }
}