package mogged.neoforge.client;

import mogged.Mogged;
import mogged.client.MoggedClient;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

/** The mod's client entry point (1.21.1, NeoForge 21.1): only built on the physical client. */
@Mod(value = Mogged.MOD_ID, dist = Dist.CLIENT)
public final class MoggedNeoForgeClient {
    private static KeyMapping toggleKey;

    public MoggedNeoForgeClient(IEventBus modBus) {
        toggleKey = MoggedClient.init();
        modBus.addListener(MoggedNeoForgeClient::onRegisterKeys);
        NeoForge.EVENT_BUS.addListener(MoggedNeoForgeClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(MoggedNeoForgeClient::onLoggingIn);
        NeoForge.EVENT_BUS.addListener(MoggedNeoForgeClient::onLoggingOut);
        // Lowest priority: after other mods have added their buttons, as the Fabric version's late phase
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, MoggedNeoForgeClient::onScreenInit);
    }

    private static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(toggleKey);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        MoggedClient.onEndTick();
    }

    private static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        MoggedClient.onJoin();
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        MoggedClient.onDisconnect();
    }

    private static void onScreenInit(ScreenEvent.Init.Post event) {
        MoggedClient.onScreenInit(event.getScreen(), event.getListenersList(), event::addListener);
    }
}
