package mogged.client.mixin;

import mogged.client.ClientSkins;
import mogged.client.SkinView;
import mogged.client.platform.ClientPlatform;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Player rendering in the world (body, cape and elytra layers all read getSkin). */
@Mixin(value = AbstractClientPlayer.class, priority = 2000)
public abstract class AbstractClientPlayerMixin {
    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void mogged$getSkin(CallbackInfoReturnable<PlayerSkin> cir) {
        PlayerSkin vanilla = cir.getReturnValue();
        if (vanilla == null) {
            return;
        }
        SkinView view = ClientPlatform.view(vanilla);
        SkinView modified = ClientSkins.modify(((AbstractClientPlayer) (Object) this).getUUID(), view);
        if (modified != view) {
            cir.setReturnValue(ClientPlatform.build(vanilla, modified));
        }
    }
}
