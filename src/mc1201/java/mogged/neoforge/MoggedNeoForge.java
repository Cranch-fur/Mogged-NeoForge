package mogged.neoforge;

import mogged.Mogged;
import mogged.neoforge.client.NeoForgeClient;
import mogged.platform.ModNetworking;
import mogged.server.SkinServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * The mod's entry point (1.20.1, NeoForge 47.1). NeoForge for 1.20.1 is a fork of Forge 47 and keeps its
 * packages (net.minecraftforge) and APIs.
 */
@Mod(Mogged.MOD_ID)
public final class MoggedNeoForge {
    public MoggedNeoForge() {
        ModNetworking.registerPayloads();
        SkinServer.init();
        MinecraftForge.EVENT_BUS.addListener(MoggedNeoForge::onServerStarting);
        MinecraftForge.EVENT_BUS.addListener(MoggedNeoForge::onServerStopped);
        MinecraftForge.EVENT_BUS.addListener(MoggedNeoForge::onLoggedIn);
        MinecraftForge.EVENT_BUS.addListener(MoggedNeoForge::onLoggedOut);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            NeoForgeClient.init(FMLJavaModLoadingContext.get().getModEventBus());
        }
    }

    private static void onServerStarting(ServerStartingEvent event) {
        SkinServer.onServerStarting(event.getServer());
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        SkinServer.onServerStopped();
    }

    private static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SkinServer.onJoin(player);
        }
    }

    private static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SkinServer.onLeave(player);
        }
    }
}
