package pl.autorynek;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import pl.autorynek.mixin.HandledScreenAccessor;

public class AutoRynekMod implements ClientModInitializer {

    public static KeyBinding openKey;
    public static KeyBinding toggleKey;

    @Override
    @SuppressWarnings("deprecation")
    public void onInitializeClient() {
        Config.get();

        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.autorynek.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, "key.categories.autorynek"));
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.autorynek.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "key.categories.autorynek"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.wasPressed()) {
                if (client.currentScreen == null) client.setScreen(new ConfigScreen());
            }
            while (toggleKey.wasPressed()) {
                if (MarketBot.running) {
                    MarketBot.stop();
                    chat("Auto Rynek wyłączony.");
                } else {
                    MarketBot.start();
                    chat("Auto Rynek włączony.");
                }
            }
            MarketBot.tick(client);
        });

        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> MarketBot.renderHud(drawContext));

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) MarketBot.onMessage(message.getString());
        });

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof GenericContainerScreen gcs) {
                ScreenKeyboardEvents.allowKeyPress(screen)
                        .register((s, key, scancode, modifiers) -> onContainerKey(gcs, key, scancode, modifiers));
            }
        });
    }

    /** @return false = zablokuj klawisz (nie przekazuj dalej do okna). */
    private static boolean onContainerKey(GenericContainerScreen screen, int key, int scancode, int modifiers) {
        MinecraftClient mc = MinecraftClient.getInstance();

        // ESC albo klawisz otwierający okno zatrzymuje bota, gdy patrzymy na rynek otwarty przez niego
        if (MarketBot.isBotScreen(screen)) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                MarketBot.stop();
                chat("Auto Rynek wyłączony (ESC).");
                return true; // pozwól zamknąć okno
            }
            if (openKey.matchesKey(key, scancode)) {
                MarketBot.stop();
                chat("Auto Rynek wyłączony.");
                if (mc.player != null) mc.player.closeHandledScreen();
                return false;
            }
        }

        // Ctrl+A na przedmiocie w rynku = dodaj do listy skupu
        if (key == GLFW.GLFW_KEY_A && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && !MarketBot.running) {
            Slot slot = ((HandledScreenAccessor) screen).getFocusedSlot();
            if (slot == null || !slot.hasStack() || mc.player == null) return true;
            if (slot.inventory == mc.player.getInventory()) return true; // tylko przedmioty z rynku, nie z ekwipunku
            ItemStack stack = slot.getStack();
            String name = ItemUtil.plainName(stack);
            if (name.isEmpty()) return true;
            double perPiece = ItemUtil.pricePerPiece(stack);
            mc.setScreen(new AddItemScreen(name, perPiece, ItemUtil.itemId(stack), stack.copy()));
            return false;
        }
        return true;
    }

    public static void chat(String text) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) mc.player.sendMessage(Text.literal("[AutoRynek] " + text), false);
    }
}
