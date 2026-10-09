package pl.autorynek;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** Główne okno "AUTO RYNEK": lista skupu, czasy, komenda rynku, tryb, START/STOP. */
public class ConfigScreen extends Screen {
    private static final int GOLD = 0xFFFFAA00;
    private static final int GRAY = 0xFFAAAAAA;
    private static final int DARK_GRAY = 0xFF777777;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GREEN = 0xFF55FF55;
    private static final int RED = 0xFFFF5555;
    private static final int YELLOW = 0xFFFFFF55;

    private static final int ROW_H = 16;

    private TextFieldWidget nameField, maxField, clickField, scanField, cmdField;
    private ButtonWidget singleBtn, allBtn, startBtn;

    private int px, py, pw, ph, listTop, listBottom;
    private int scroll;
    private String message = "";
    private int messageColor = RED;

    public ConfigScreen() {
        super(Text.literal("Auto Rynek"));
    }

    @Override
    protected void init() {
        Config cfg = Config.get();

        // zachowaj wpisany tekst przy zmianie rozmiaru okna
        String vName = nameField == null ? "" : nameField.getText();
        String vMax = maxField == null ? "" : maxField.getText();
        String vClick = clickField == null ? String.valueOf(cfg.clickMs) : clickField.getText();
        String vScan = scanField == null ? String.valueOf(cfg.scanMs) : scanField.getText();
        String vCmd = cmdField == null ? cfg.marketCommand : cmdField.getText();

        pw = Math.min(560, width - 20);
        ph = Math.min(330, height - 20);
        px = (width - pw) / 2;
        py = (height - ph) / 2;

        int maxW = 90, addW = 64, gap = 8;
        int nameW = pw - 20 - maxW - addW - 2 * gap;

        nameField = new TextFieldWidget(textRenderer, px + 10, py + 44, nameW, 16, Text.literal("Przedmiot"));
        nameField.setPlaceholder(Text.literal("Nazwa przedmiotu..."));
        nameField.setMaxLength(100);
        nameField.setText(vName);
        addDrawableChild(nameField);

        maxField = new TextFieldWidget(textRenderer, px + 10 + nameW + gap, py + 44, maxW, 16, Text.literal("Maks"));
        maxField.setPlaceholder(Text.literal("Maks $"));
        maxField.setMaxLength(20);
        maxField.setText(vMax);
        addDrawableChild(maxField);

        addDrawableChild(ButtonWidget.builder(Text.literal("+ Dodaj"), b -> addEntry())
                .dimensions(px + pw - 10 - addW, py + 43, addW, 18).build());

        clickField = new TextFieldWidget(textRenderer, px + 68, py + 76, 50, 16, Text.literal("Klik"));
        clickField.setMaxLength(5);
        clickField.setText(vClick);
        addDrawableChild(clickField);

        scanField = new TextFieldWidget(textRenderer, px + 188, py + 76, 50, 16, Text.literal("Skan"));
        scanField.setMaxLength(5);
        scanField.setText(vScan);
        addDrawableChild(scanField);

        addDrawableChild(ButtonWidget.builder(Text.literal("Zapisz"), b -> {
            if (applySettings()) flash("Zapisano.", GREEN);
        }).dimensions(px + pw - 10 - addW, py + 75, addW, 18).build());

        cmdField = new TextFieldWidget(textRenderer, px + 96, py + 100, 150, 16, Text.literal("Komenda"));
        cmdField.setMaxLength(60);
        cmdField.setText(vCmd);
        addDrawableChild(cmdField);

        singleBtn = ButtonWidget.builder(Text.empty(), b -> setMode(false))
                .dimensions(px + 256, py + 99, 90, 18).build();
        allBtn = ButtonWidget.builder(Text.empty(), b -> setMode(true))
                .dimensions(px + 350, py + 99, 90, 18).build();
        addDrawableChild(singleBtn);
        addDrawableChild(allBtn);
        refreshModeButtons();

        startBtn = ButtonWidget.builder(Text.empty(), b -> toggleBot())
                .dimensions(px + (pw - 160) / 2, py + ph - 28, 160, 18).build();
        addDrawableChild(startBtn);
        refreshStartButton();

        listTop = py + 150;
        listBottom = py + ph - 52;
        clampScroll();
    }

    // ------------------------------------------------------------------ akcje

    private void setMode(boolean all) {
        Config.get().buyAll = all;
        Config.get().save();
        refreshModeButtons();
    }

    private void refreshModeButtons() {
        boolean all = Config.get().buyAll;
        singleBtn.setMessage(Text.literal((all ? "□ " : "■ ") + "Pojedynczo"));
        allBtn.setMessage(Text.literal((all ? "■ " : "□ ") + "Wszystkie"));
    }

    private void refreshStartButton() {
        startBtn.setMessage(Text.literal(MarketBot.running ? "■ STOP" : "▶ START"));
    }

    private void toggleBot() {
        if (MarketBot.running) {
            MarketBot.stop();
            refreshStartButton();
            return;
        }
        if (!applySettings()) return;
        if (Config.get().items.isEmpty()) {
            flash("Lista skupu jest pusta - dodaj przynajmniej jeden przedmiot.", RED);
            return;
        }
        MarketBot.start();
        close(); // bot otwiera rynek tylko, gdy nie ma otwartego okna
    }

    private void addEntry() {
        String name = nameField.getText().trim();
        double max = PriceParser.fromInput(maxField.getText());
        if (name.isEmpty()) {
            flash("Wpisz nazwę przedmiotu.", RED);
            return;
        }
        if (Double.isNaN(max) || max <= 0) {
            flash("Podaj poprawną cenę, np. 60, 1.5k, 2m.", RED);
            return;
        }
        Config cfg = Config.get();
        cfg.upsert(name, max, null);
        cfg.save();
        nameField.setText("");
        maxField.setText("");
        flash("Dodano: " + name, GREEN);
    }

    private boolean applySettings() {
        Config cfg = Config.get();
        try {
            cfg.clickMs = Math.max(50, Integer.parseInt(clickField.getText().trim()));
            cfg.scanMs = Math.max(100, Integer.parseInt(scanField.getText().trim()));
        } catch (NumberFormatException ex) {
            flash("Klik i Skan muszą być liczbami (ms).", RED);
            return false;
        }
        String cmd = cmdField.getText().trim();
        if (!cmd.isEmpty()) cfg.marketCommand = cmd;
        cfg.save();
        return true;
    }

    private void flash(String text, int color) {
        message = text;
        messageColor = color;
    }

    @Override
    public void removed() {
        if (clickField != null) applySettings();
    }

    // ------------------------------------------------------------------ lista

    private int visibleRows() {
        return Math.max(1, (listBottom - listTop) / ROW_H);
    }

    private void clampScroll() {
        int max = Math.max(0, Config.get().items.size() - visibleRows());
        scroll = Math.max(0, Math.min(scroll, max));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scroll -= (int) Math.signum(verticalAmount);
        clampScroll();
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseY >= listTop && mouseY < listBottom) {
            int row = (int) ((mouseY - listTop) / ROW_H) + scroll;
            List<Config.Entry> items = Config.get().items;
            int delX = px + pw - 34;
            if (row >= 0 && row < items.size() && mouseX >= delX && mouseX < delX + 20) {
                items.remove(row);
                Config.get().save();
                clampScroll();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)
                && (nameField.isFocused() || maxField.isFocused())) {
            addEntry();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ------------------------------------------------------------------ rysowanie

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // własne tło rysujemy w render() (bez rozmycia)
    }

    @Override
    public void render(DrawContext c, int mouseX, int mouseY, float delta) {
        Config cfg = Config.get();

        c.fill(0, 0, width, height, 0x70000000);
        c.fill(px, py, px + pw, py + ph, 0xF0101820);
        c.fill(px, py + 18, px + pw, py + 19, GOLD);

        c.drawCenteredTextWithShadow(textRenderer, "AUTO RYNEK", width / 2, py + 5, GOLD);
        String status = MarketBot.running ? "● WŁĄCZONY" : "● WYŁĄCZONY";
        c.drawTextWithShadow(textRenderer, status, px + pw - 8 - textRenderer.getWidth(status), py + 5,
                MarketBot.running ? GREEN : RED);

        // etykiety
        c.drawTextWithShadow(textRenderer, "Przedmiot:", px + 10, py + 32, GRAY);
        c.drawTextWithShadow(textRenderer, "Maks $/szt.:", px + pw - 10 - 64 - 8 - 90, py + 32, GRAY);
        c.drawTextWithShadow(textRenderer, "Klik (ms):", px + 10, py + 80, GRAY);
        c.drawTextWithShadow(textRenderer, "Skan (ms):", px + 130, py + 80, GRAY);
        c.drawTextWithShadow(textRenderer, "Komenda rynku:", px + 10, py + 104, GRAY);

        // nagłówek listy
        c.fill(px + 10, py + 124, px + pw - 10, py + 125, 0xFF303A46);
        c.drawTextWithShadow(textRenderer, "LISTA SKUPU (" + cfg.items.size() + ")", px + 10, py + 130, GOLD);
        String hint = "Ctrl+A na itemie w rynku = dodaj";
        c.drawTextWithShadow(textRenderer, hint, px + pw - 10 - textRenderer.getWidth(hint), py + 130, DARK_GRAY);
        c.drawTextWithShadow(textRenderer, "Nazwa", px + 34, py + 141, DARK_GRAY);
        c.drawTextWithShadow(textRenderer, "Maks $/szt.", px + pw - 160, py + 141, DARK_GRAY);

        // wiersze
        clampScroll();
        List<Config.Entry> items = cfg.items;
        int rows = visibleRows();
        for (int i = 0; i < rows && i + scroll < items.size(); i++) {
            Config.Entry e = items.get(i + scroll);
            int y = listTop + i * ROW_H;
            c.fill(px + 10, y, px + pw - 10, y + ROW_H - 1, (i % 2 == 0) ? 0x30FFFFFF : 0x18FFFFFF);

            ItemStack icon = iconFor(e);
            if (!icon.isEmpty()) {
                c.getMatrices().push();
                c.getMatrices().translate(px + 12, y, 0);
                c.getMatrices().scale(0.75f, 0.75f, 1f);
                c.drawItem(icon, 0, 0);
                c.getMatrices().pop();
            }
            c.drawTextWithShadow(textRenderer, e.name, px + 34, y + 4, WHITE);

            int priceColor = switch (e.state) {
                case GOOD -> GREEN;
                case TOO_EXPENSIVE -> RED;
                case NO_PRICE -> YELLOW;
                default -> GREEN;
            };
            c.drawTextWithShadow(textRenderer, Fmt.money2(e.maxPrice), px + pw - 160, y + 4, priceColor);

            int delX = px + pw - 34;
            boolean hover = mouseX >= delX && mouseX < delX + 20 && mouseY >= y && mouseY < y + ROW_H - 1;
            c.fill(delX, y + 1, delX + 20, y + ROW_H - 2, hover ? 0xFFB04040 : 0xFF7A3038);
            c.drawCenteredTextWithShadow(textRenderer, "x", delX + 10, y + 4, WHITE);
        }

        // stopka listy
        int total = items.size();
        String range = total == 0 ? "0 z 0" : (scroll + 1) + "-" + Math.min(total, scroll + rows) + " z " + total;
        c.drawTextWithShadow(textRenderer, range, px + 10, py + ph - 44, DARK_GRAY);

        // legenda
        int ly = py + ph - 44;
        int lx = px + 110;
        lx = legend(c, lx, ly, GREEN, "dobra cena");
        lx = legend(c, lx, ly, RED, "za drogi");
        legend(c, lx, ly, YELLOW, "brak ceny w lore");

        if (!message.isEmpty()) {
            c.drawTextWithShadow(textRenderer, message, px + 10, py + ph - 14, messageColor);
        }

        refreshStartButton();
        super.render(c, mouseX, mouseY, delta);
    }

    private int legend(DrawContext c, int x, int y, int color, String text) {
        c.fill(x, y, x + 7, y + 7, color);
        c.drawTextWithShadow(textRenderer, text, x + 11, y, GRAY);
        return x + 11 + textRenderer.getWidth(text) + 14;
    }

    private ItemStack iconFor(Config.Entry e) {
        if (e.itemId == null) return ItemStack.EMPTY;
        Identifier id = Identifier.tryParse(e.itemId);
        if (id == null || !Registries.ITEM.containsId(id)) return ItemStack.EMPTY;
        return new ItemStack(Registries.ITEM.get(id));
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
