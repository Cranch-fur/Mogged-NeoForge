package mogged.client.mixin;

import com.mojang.authlib.GameProfile;
import mogged.client.ClientSkins;
import mogged.client.SkinView;
import mogged.client.platform.ClientPlatform;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Tab list faces and other places that read the skin from player info. */
@Mixin(PlayerInfo.class)
public abstract class PlayerInfoMixin {
    @Shadow
    public abstract GameProfile getProfile();

    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void mogged$getSkin(CallbackInfoReturnable<PlayerSkin> cir) {
        GameProfile profile = getProfile();
        PlayerSkin vanilla = cir.getReturnValue();
        if (profile == null || vanilla == null) {
            return;
        }
        SkinView view = ClientPlatform.view(vanilla);
        SkinView modified = ClientSkins.modify(profile.id(), view);
        if (modified != view) {
            cir.setReturnValue(ClientPlatform.build(vanilla, modified));
        }
    }
}
