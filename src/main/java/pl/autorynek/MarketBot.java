package pl.autorynek;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Logika Auto Rynku.
 *
 * Pętla (co tick klienta):
 *  1. Rynek zamknięty -> wyślij komendę rynku (np. /ah open) co najmniej co "Skan" ms.
 *  2. Rynek otwarty przez bota -> przejrzyj sloty skrzyni, znajdź oferty z listy skupu,
 *     których cena za sztukę <= "Maks $/szt.", kliknij najtańszą (odstęp "Klik" ms).
 *  3. Brak okazji -> po "Skan" ms odśwież rynek (zamknij i otwórz ponownie lub kliknij slot odświeżania).
 */
public final class MarketBot {
    private MarketBot() {}

    public static volatile boolean running;

    // statystyki do HUD
    public static int bought;
    public static int missed;
    public static double spent;
    public static double saved;
    public static String lastClick = "-";

    private static long startedAt;
    private static long lastCommand;
    private static long lastClickAt;
    private static long openedAt;
    private static boolean awaitingMarket;
    private static boolean viewDone;
    private static int marketSyncId = -1;

    private record Pending(long time, Config.Entry entry, double price, int count) {}

    private static Pending pending;
    /** sloty już klikane w bieżącym widoku rynku (żeby nie klikać w kółko tej samej oferty) */
    private static final Set<Integer> clicked = new HashSet<>();

    // ---------------------------------------------------------------- start / stop

    public static void start() {
        bought = 0;
        missed = 0;
        spent = 0;
        saved = 0;
        lastClick = "-";
        pending = null;
        clicked.clear();
        awaitingMarket = false;
        marketSyncId = -1;
        viewDone = false;
        confirmSyncId = -1;
        confirmClicked = false;
        startedAt = System.currentTimeMillis();
        lastCommand = 0;
        running = true;
    }

    public static void stop() {
        running = false;
        awaitingMarket = false;
        marketSyncId = -1;
        pending = null;
    }

    /** Czy to okno skrzyni zostało otwarte przez bota (a nie ręcznie przez gracza)? */
    public static boolean isBotScreen(GenericContainerScreen screen) {
        if (!running) return false;
        int id = screen.getScreenHandler().syncId;
        return (marketSyncId != -1 && id == marketSyncId) || (confirmSyncId != -1 && id == confirmSyncId);
    }

    // ---------------------------------------------------------------- tick

    public static void tick(MinecraftClient mc) {
        if (!running) return;

        ClientPlayerEntity player = mc.player;
        ClientPlayNetworkHandler net = mc.getNetworkHandler();
        if (player == null || net == null || mc.interactionManager == null) {
            stop(); // rozłączono / zmiana świata
            return;
        }

        long now = System.currentTimeMillis();
        Config cfg = Config.get();

        if (pending != null && now - pending.time() > 5000) pending = null;
        if (awaitingMarket && now - lastCommand > 4000) awaitingMarket = false;

        Screen screen = mc.currentScreen;

        if (screen instanceof GenericContainerScreen gcs) {
            GenericContainerScreenHandler handler = gcs.getScreenHandler();
            if (awaitingMarket) {
                awaitingMarket = false;
                marketSyncId = handler.syncId;
                openedAt = now;
                viewDone = false;
                clicked.clear();
            }
            // tuż po kliknięciu oferty: okno "POTWIERDŹ ZAKUP" (nowe okno albo to samo z podmienioną zawartością)
            if (handleConfirm(mc, player, gcs, handler, cfg, now)) return;
            if (handler.syncId != marketSyncId) return; // cudza skrzynia - nie ruszamy
            handleMarket(mc, player, handler, cfg, now);
            return;
        }

        marketSyncId = -1;
        if (screen == null) {
            long gap = Math.max(cfg.scanMs, 500);
            if (now - lastCommand >= gap) {
                String cmd = cfg.marketCommand.trim();
                if (cmd.startsWith("/")) cmd = cmd.substring(1);
                if (!cmd.isEmpty()) net.sendChatCommand(cmd);
                lastCommand = now;
                awaitingMarket = true;
            }
        }
    }

    private record Candidate(int slot, Config.Entry entry, double perPiece, double price, int count, String name) {}

    private static void handleMarket(MinecraftClient mc, ClientPlayerEntity player,
                                     GenericContainerScreenHandler handler, Config cfg, long now) {
        if (now - openedAt < 200) return; // poczekaj aż serwer dośle zawartość okna

        int chestSlots = Math.min(handler.getRows() * 9, handler.slots.size());
        int nonEmpty = 0;
        Candidate best = null;
        Map<Config.Entry, Config.State> seen = new HashMap<>();

        for (int i = 0; i < chestSlots; i++) {
            ItemStack stack = handler.slots.get(i).getStack();
            if (stack.isEmpty()) continue;
            nonEmpty++;

            String name = ItemUtil.plainName(stack);
            Config.Entry entry = cfg.find(name);
            if (entry == null) continue;

            double price = ItemUtil.listingPrice(stack);
            Config.State st;
            if (Double.isNaN(price)) {
                st = Config.State.NO_PRICE;
            } else {
                double perPiece = price / Math.max(1, stack.getCount());
                if (perPiece <= entry.maxPrice + 1e-9) {
                    st = Config.State.GOOD;
                    if (!clicked.contains(i) && (best == null || perPiece < best.perPiece())) {
                        best = new Candidate(i, entry, perPiece, price, stack.getCount(), name);
                    }
                } else {
                    st = Config.State.TOO_EXPENSIVE;
                }
            }
            Config.State prev = seen.get(entry);
            if (prev == null || rank(st) > rank(prev)) seen.put(entry, st);
        }
        seen.forEach((e, st) -> e.state = st);

        if (best != null && !viewDone) {
            if (now - lastClickAt < cfg.clickMs) return; // czekamy na odstęp między kliknięciami
            mc.interactionManager.clickSlot(handler.syncId, best.slot(), 0, SlotActionType.PICKUP, player);
            lastClickAt = now;
            clicked.add(best.slot());
            lastClick = "slot " + best.slot() + ": " + best.name();
            pending = new Pending(now, best.entry(), best.price(), best.count());
            if (!cfg.buyAll) viewDone = true;
            return;
        }

        if (nonEmpty == 0 && now - openedAt < 1500) return; // rynek jeszcze się ładuje
        if (now - openedAt >= cfg.scanMs) refresh(player, handler, mc, cfg, now);
    }

    // ---------------------------------------------------------------- okno potwierdzenia zakupu

    private static int confirmSyncId = -1;
    private static long confirmForClick;
    private static long confirmSeenAt;
    private static long confirmClickAt;
    private static boolean confirmClicked;

    /**
     * @return true, jeśli ten tick został zużyty na obsługę potwierdzenia (reszta pętli ma go pominąć).
     */
    private static boolean handleConfirm(MinecraftClient mc, ClientPlayerEntity player, GenericContainerScreen screen,
                                         GenericContainerScreenHandler handler, Config cfg, long now) {
        Pending p = pending;
        if (p == null || now - p.time() >= 5000) return false;

        if (confirmForClick != p.time()) { // nowe kliknięcie w ofertę = nowe potwierdzenie do obsłużenia
            confirmForClick = p.time();
            confirmSeenAt = now;
            confirmClicked = false;
        }
        boolean sameWindow = handler.syncId == marketSyncId;
        confirmSyncId = sameWindow ? -1 : handler.syncId;

        if (confirmClicked) {
            if (sameWindow) return false; // po potwierdzeniu wracamy do zwykłej pętli rynku
            if (now - confirmClickAt > 3000) player.closeHandledScreen(); // okno utknęło - zamknij
            return true;
        }
        if (now - confirmSeenAt < Math.max(100, cfg.clickMs)) return true; // poczekaj na zawartość okna

        int slot = findConfirmSlot(handler, cfg, !sameWindow);
        if (slot >= 0) {
            mc.interactionManager.clickSlot(handler.syncId, slot, 0, SlotActionType.PICKUP, player);
            lastClick = "potwierdź: slot " + slot;
            confirmClicked = true;
            confirmClickAt = now;
            return true;
        }

        if (now - confirmSeenAt > 1500) {
            confirmClicked = true; // koniec czekania
            confirmClickAt = now;
            if (!sameWindow) {
                // nowe okno, ale bez rozpoznanego przycisku - wypisz jego zawartość na czat
                AutoRynekMod.chat("Nie znaleziono przycisku potwierdzenia. Okno: \"" + screen.getTitle().getString()
                        + "\", rzędów: " + handler.getRows());
                int shown = 0;
                int total = Math.min(handler.getRows() * 9, handler.slots.size());
                for (int i = 0; i < total && shown < 16; i++) {
                    ItemStack st = handler.slots.get(i).getStack();
                    if (st.isEmpty()) continue;
                    String lore = ItemUtil.loreLines(st).stream().findFirst().orElse("");
                    AutoRynekMod.chat("  slot " + i + ": [" + ItemUtil.itemId(st) + "] \"" + ItemUtil.plainName(st)
                            + "\" " + lore);
                    shown++;
                }
                AutoRynekMod.chat("Ustaw w autorynek.json \"confirmSlot\" na numer slotu przycisku Potwierdź.");
            }
        }
        return true;
    }

    private static int findConfirmSlot(GenericContainerScreenHandler handler, Config cfg, boolean allowColorGuess) {
        if (cfg.confirmSlot >= 0 && cfg.confirmSlot < handler.slots.size()) return cfg.confirmSlot;
        int total = Math.min(handler.getRows() * 9, handler.slots.size());
        String[] keys = {"potwierd", "zatwierd", "akcept"};
        // 1) po nazwie przedmiotu
        for (int i = 0; i < total; i++) {
            ItemStack st = handler.slots.get(i).getStack();
            if (st.isEmpty()) continue;
            String n = Config.normalize(ItemUtil.plainName(st));
            if (n.contains("anuluj") || n.contains("odrzu")) continue;
            for (String k : keys) if (n.contains(k)) return i;
        }
        // 2) po treści opisu (lore)
        for (int i = 0; i < total; i++) {
            ItemStack st = handler.slots.get(i).getStack();
            if (st.isEmpty()) continue;
            String n = Config.normalize(ItemUtil.plainName(st));
            if (n.contains("anuluj") || n.contains("odrzu")) continue;
            for (String line : ItemUtil.loreLines(st)) {
                String l = Config.normalize(line);
                for (String k : keys) if (l.contains(k)) return i;
            }
        }
        // 3) tylko w nowym oknie: zielony przedmiot (lime/green) = potwierdź
        if (allowColorGuess) {
            for (int i = 0; i < total; i++) {
                ItemStack st = handler.slots.get(i).getStack();
                if (st.isEmpty()) continue;
                String id = ItemUtil.itemId(st);
                if ((id.contains("lime") || id.contains("green")) && !id.contains("red")) return i;
            }
        }
        return -1;
    }

    private static int rank(Config.State s) {
        return switch (s) {
            case GOOD -> 3;
            case TOO_EXPENSIVE -> 2;
            case NO_PRICE -> 1;
            default -> 0;
        };
    }

    private static void refresh(ClientPlayerEntity player, GenericContainerScreenHandler handler,
                                MinecraftClient mc, Config cfg, long now) {
        if (cfg.refreshSlot >= 0 && cfg.refreshSlot < handler.slots.size()) {
            mc.interactionManager.clickSlot(handler.syncId, cfg.refreshSlot, 0, SlotActionType.PICKUP, player);
            lastClick = "slot " + cfg.refreshSlot + ": odświeżenie";
            openedAt = now;
            viewDone = false;
            clicked.clear();
        } else {
            player.closeHandledScreen(); // następny tick otworzy rynek komendą od nowa
            marketSyncId = -1;
        }
    }

    // ---------------------------------------------------------------- czat -> statystyki

    public static void onMessage(String raw) {
        Pending p = pending;
        if (!running || p == null) return;
        String msg = Formatting.strip(raw);
        if (msg == null) return;
        Config cfg = Config.get();
        try {
            if (Pattern.compile(cfg.buyPattern).matcher(msg).find()) {
                bought += p.count();
                spent += p.price();
                saved += Math.max(0, p.entry().maxPrice * p.count() - p.price());
                pending = null;
            } else if (Pattern.compile(cfg.missPattern).matcher(msg).find()) {
                missed++;
                pending = null;
            }
        } catch (Exception ignored) {
            // błędny regex w configu - ignorujemy
        }
    }

    // ---------------------------------------------------------------- HUD

    private static final int GOLD = 0xFFFFAA00;
    private static final int GRAY = 0xFFAAAAAA;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GREEN = 0xFF55FF55;
    private static final int RED = 0xFFFF5555;
    private static final int YELLOW = 0xFFFFFF55;

    public static void renderHud(DrawContext ctx) {
        if (!running) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options.hudHidden) return;
        TextRenderer tr = mc.textRenderer;

        String mode = Config.get().buyAll ? "Wszyst." : "Pojed.";
        String[] labels = {"Czas:", "Kupione:", "Wydano:", "Zaoszczędzono:", "Przegapione:", "Klik:"};
        String[] values = {
                Fmt.time(System.currentTimeMillis() - startedAt),
                bought + " szt.",
                Fmt.money(spent),
                Fmt.money(saved),
                String.valueOf(missed),
                lastClick
        };
        int[] colors = {WHITE, WHITE, RED, GREEN, RED, YELLOW};
        String suffix = " (ktoś ubiegł)";

        int labelW = 0;
        for (String l : labels) labelW = Math.max(labelW, tr.getWidth(l));
        int valueW = 0;
        for (int i = 0; i < values.length; i++) {
            int w = tr.getWidth(values[i]) + (i == 4 ? tr.getWidth(suffix) : 0);
            valueW = Math.max(valueW, w);
        }
        int titleW = tr.getWidth("AUTO RYNEK " + mode);
        int w = Math.max(titleW, labelW + 6 + valueW);
        int lineH = 10;
        int h = lineH * (labels.length + 1);

        int x = mc.getWindow().getScaledWidth() - w - 8;
        int y = 8;

        ctx.fill(x - 6, y - 3, x + w + 4, y + h + 3, 0xB0000000);
        ctx.fill(x - 6, y - 3, x - 4, y + h + 3, GOLD);

        ctx.drawTextWithShadow(tr, "AUTO RYNEK", x, y, GOLD);
        ctx.drawTextWithShadow(tr, mode, x + tr.getWidth("AUTO RYNEK "), y, GREEN);
        for (int i = 0; i < labels.length; i++) {
            int ly = y + lineH * (i + 1);
            ctx.drawTextWithShadow(tr, labels[i], x, ly, GRAY);
            ctx.drawTextWithShadow(tr, values[i], x + labelW + 6, ly, colors[i]);
            if (i == 4) {
                ctx.drawTextWithShadow(tr, suffix, x + labelW + 6 + tr.getWidth(values[i]), ly, GRAY);
            }
        }
    }
}
