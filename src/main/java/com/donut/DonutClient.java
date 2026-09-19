package com.donut;

import com.donut.config.ConfigManager;
import com.donut.config.ProfileManager;
import com.donut.event.EventBus;
import com.donut.event.events.ConnectionEvent;
import com.donut.event.events.TickEvent;
import com.donut.gui.clickgui.ClickGUI;
import com.donut.gui.hud.HudRenderer;
import com.donut.module.Module;
import com.donut.module.ModuleManager;
import com.donut.module.modules.AutoMine;
import com.donut.module.modules.HudOverlay;
import com.donut.module.modules.InventoryManager;
import com.donut.module.modules.PathfinderModule;
import com.donut.module.modules.SchematicBuilder;
import com.donut.pathfinding.MovementInputOverride;
import com.donut.rotation.RotationManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Donut Client entrypoint: wires the event bus, module registry, config,
 * profiles, HUD and the ClickGUI to Fabric lifecycle events.
 */
public final class DonutClient implements ClientModInitializer {
    public static final String MOD_ID = "donut";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

    private static EventBus bus;
    private static ModuleManager modules;
    private static HudRenderer hud;
    private static SchematicBuilder builderModule;

    private final Map<Module, Boolean> prevKeyStates = new IdentityHashMap<>();
    private boolean prevGuiKey;

    public static EventBus bus() {
        return bus;
    }

    public static ModuleManager modules() {
        return modules;
    }

    @Override
    public void onInitializeClient() {
        bus = new EventBus();
        modules = new ModuleManager();

        AutoMine autoMine = new AutoMine();
        builderModule = new SchematicBuilder();
        PathfinderModule pathfinder = new PathfinderModule();
        InventoryManager inventory = new InventoryManager();
        HudOverlay hudModule = new HudOverlay();

        for (Module m : new Module[]{autoMine, builderModule, pathfinder, inventory, hudModule}) {
            modules.register(m);
            m.setBus(bus);
        }

        var configDir = FabricLoader.getInstance().getConfigDir();
        ConfigManager.init(configDir, modules, bus);
        ProfileManager.init(configDir, modules);
        builderModule.setSchematicsDir(FabricLoader.getInstance().getGameDir().resolve("schematics"));

        hud = new HudRenderer(modules, hudModule);

        ClientTickEvents.END_CLIENT_TICK.register(this::onEndTick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            ConfigManager.save();
            builderModule.stop();
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
                bus.post(new ConnectionEvent(true, serverAddress(handler.getServerInfo() != null
                        ? handler.getServerInfo().address : null))));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            MovementInputOverride.end();
            RotationManager.clear();
            bus.post(new ConnectionEvent(false, serverAddress(null)));
        });
        HudRenderCallback.EVENT.register((g, tickDelta) -> hud.render(g, MinecraftClient.getInstance()));

        LOG.info("Donut Client initialized with {} modules", modules.all().size());
    }

    private static String serverAddress(String addr) {
        return addr == null ? "singleplayer" : addr;
    }

    private void onEndTick(MinecraftClient client) {
        RotationManager.tick(client);
        bus.post(new TickEvent(client));

        if (client.player == null || client.currentScreen != null) {
            prevGuiKey = false;
            prevKeyStates.clear();
            return;
        }

        long window = client.getWindow().getHandle();

        // Open the ClickGUI on the rising edge of the configured key
        boolean guiPressed = GLFW.glfwGetKey(window, ConfigManager.guiOpenKey) == GLFW.GLFW_PRESS;
        if (guiPressed && !prevGuiKey) {
            client.setScreen(new ClickGUI(modules));
        }
        prevGuiKey = guiPressed;

        // Module keybinds: rising-edge toggle with modifier awareness
        int mods = 0;
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS) mods |= 1;
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS) mods |= 2;
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS) mods |= 4;

        for (Module m : modules.all()) {
            int code = m.keybind.get();
            if (code == 0) continue;
            boolean down = GLFW.glfwGetKey(window, code) == GLFW.GLFW_PRESS;
            boolean prev = prevKeyStates.getOrDefault(m, false);
            if (down && !prev && m.keybind.matches(code, mods)) {
                m.toggle();
            }
            prevKeyStates.put(m, down);
        }
    }
}
