package mogged.neoforge;

import mogged.Mogged;
import mogged.platform.ModNetworking;
import mogged.server.SkinServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/** The mod's entry point on both sides (26.x, NeoForge); the client's is MoggedNeoForgeClient. */
@Mod(Mogged.MOD_ID)
public final class MoggedNeoForge {
    public MoggedNeoForge(IEventBus modBus) {
        ModNetworking.registerPayloads(modBus);
        SkinServer.init();
        NeoForge.EVENT_BUS.addListener(MoggedNeoForge::onServerStarting);
        NeoForge.EVENT_BUS.addListener(MoggedNeoForge::onServerStopped);
        NeoForge.EVENT_BUS.addListener(MoggedNeoForge::onLoggedIn);
        NeoForge.EVENT_BUS.addListener(MoggedNeoForge::onLoggedOut);
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
