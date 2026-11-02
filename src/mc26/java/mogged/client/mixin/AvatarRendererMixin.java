package mogged.client.mixin;

import mogged.client.ClientSkins;
import mogged.client.PlayerSkins;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cape mods (e.g. BetterCapes) replace the cape and elytra in the render state after the skin is read.
 * The high priority makes this run after them, so a preset with its own cape or elytra still wins.
 * Presets without their own cape or elytra leave the state alone, so cape mods keep working there.
 */
@Mixin(value = AvatarRenderer.class, priority = 2000)
public abstract class AvatarRendererMixin {
    @Inject(
            method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
            at = @At("RETURN"))
    private void mogged$keepPresetCosmetics(Avatar entity, AvatarRenderState state, float partialTick, CallbackInfo ci) {
        if (!(entity instanceof AbstractClientPlayer player) || state.skin == null) {
            return;
        }
        PlayerSkins skins = ClientSkins.skinsFor(player.getUUID());
        if (skins == null || !skins.hasOwnCosmetics()) {
            return;
        }
        // getSkin() goes through our mixins and returns the preset (or transition) skin
        PlayerSkin ours = player.getSkin();
        if (ours.cape() == state.skin.cape() && ours.elytra() == state.skin.elytra()) {
            return;
        }
        state.skin = new PlayerSkin(state.skin.body(), ours.cape(), ours.elytra(), state.skin.model(), state.skin.secure());
    }
}
