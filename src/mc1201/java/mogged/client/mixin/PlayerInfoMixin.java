package mogged.client.mixin;

import com.mojang.authlib.GameProfile;
import mogged.client.ClientSkins;
import mogged.client.SkinView;
import mogged.client.platform.ClientPlatform;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 1.20.1 has no skin record: players in the world, the tab list and everything else read the
 * skin, cape, elytra and model from player info one by one, so each getter is replaced here.
 */
@Mixin(value = PlayerInfo.class, priority = 2000)
public abstract class PlayerInfoMixin {
    @Shadow
    public abstract GameProfile getProfile();

    @Unique
    private SkinView mogged$modified() {
        if (ClientPlatform.readingVanilla()) {
            return null;
        }
        GameProfile profile = getProfile();
        if (profile == null) {
            return null;
        }
        SkinView view = ClientPlatform.view((PlayerInfo) (Object) this);
        SkinView modified = ClientSkins.modify(profile.getId(), view);
        return modified != view ? modified : null;
    }

    @Inject(method = "getSkinLocation", at = @At("RETURN"), cancellable = true)
    private void mogged$skin(CallbackInfoReturnable<ResourceLocation> cir) {
        SkinView modified = mogged$modified();
        if (modified != null && modified.body() != null) {
            cir.setReturnValue(ClientPlatform.id(modified.body()));
        }
    }

    @Inject(method = "getCapeLocation", at = @At("RETURN"), cancellable = true)
    private void mogged$cape(CallbackInfoReturnable<ResourceLocation> cir) {
        SkinView modified = mogged$modified();
        if (modified != null) {
            cir.setReturnValue(ClientPlatform.id(modified.cape()));
        }
    }

    @Inject(method = "getElytraLocation", at = @At("RETURN"), cancellable = true)
    private void mogged$elytra(CallbackInfoReturnable<ResourceLocation> cir) {
        SkinView modified = mogged$modified();
        if (modified != null) {
            cir.setReturnValue(ClientPlatform.id(modified.elytra()));
        }
    }

    @Inject(method = "getModelName", at = @At("RETURN"), cancellable = true)
    private void mogged$model(CallbackInfoReturnable<String> cir) {
        SkinView modified = mogged$modified();
        if (modified != null) {
            cir.setReturnValue(modified.slim() ? "slim" : "default");
        }
    }
}
