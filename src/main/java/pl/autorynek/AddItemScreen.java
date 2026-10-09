package pl.autorynek;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/** Okienko "DODAJ PRZEDMIOT DO AUTO-RYNKU" otwierane przez Ctrl+A na przedmiocie w rynku. */
public class AddItemScreen extends Screen {
    private static final int GOLD = 0xFFFFAA00;
    private static final int GRAY = 0xFFAAAAAA;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GREEN = 0xFF55FF55;
    private static final int RED = 0xFFFF5555;
    private static final int YELLOW = 0xFFFFFF55;

    private final String itemName;
    private final double detectedPrice;
    private final String itemId;
    private final ItemStack icon;

    private TextFieldWidget maxField;
    private String error = "";
    private int px, py;
    private static final int W = 340;
    private static final int H = 150;

    public AddItemScreen(String itemName, double detectedPrice, String itemId, ItemStack icon) {
        super(Text.literal("Dodaj przedmiot do Auto-Rynku"));
        this.itemName = itemName;
        this.detectedPrice = detectedPrice;
        this.itemId = itemId;
        this.icon = icon;
    }

    @Override
    protected void init() {
        px = (width - W) / 2;
        py = (height - H) / 2;

        String keep = maxField == null ? "" : maxField.getText();
        maxField = new TextFieldWidget(textRenderer, px + 12, py + 100, W - 24, 16, Text.literal("Maks cena"));
        maxField.setMaxLength(20);
        maxField.setText(keep);
        addDrawableChild(maxField);
        setInitialFocus(maxField);

        addDrawableChild(ButtonWidget.builder(Text.literal("Anuluj"), b -> close())
                .dimensions(px + 12, py + H - 28, 100, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("+ Dodaj do listy"), b -> confirm())
                .dimensions(px + W - 12 - 140, py + H - 28, 140, 18).build());
    }

    private void confirm() {
        double max = PriceParser.fromInput(maxField.getText());
        if (Double.isNaN(max) || max <= 0) {
            error = "Podaj poprawną cenę, np. 60, 1.5k, 2m";
            return;
        }
        Config cfg = Config.get();
        cfg.upsert(itemName, max, itemId);
        cfg.save();
        AutoRynekMod.chat("Dodano do skupu: " + itemName + " (max " + Fmt.money2(max) + "/szt.)");
        close();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            confirm();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // własne tło rysujemy w render() (bez rozmycia)
    }

    @Override
    public void render(DrawContext c, int mouseX, int mouseY, float delta) {
        c.fill(0, 0, width, height, 0x70000000);
        c.fill(px, py, px + W, py + H, 0xF0101820);
        c.fill(px, py + 18, px + W, py + 19, GOLD);
        c.drawCenteredTextWithShadow(textRenderer, "DODAJ PRZEDMIOT DO AUTO-RYNKU", width / 2, py + 5, GOLD);

        c.drawItem(icon, px + 12, py + 26);
        c.drawTextWithShadow(textRenderer, itemName, px + 34, py + 30, WHITE);

        if (Double.isNaN(detectedPrice)) {
            c.drawTextWithShadow(textRenderer, "Brak ceny w lore - ustaw ją ręcznie", px + 12, py + 56, YELLOW);
        } else {
            c.drawTextWithShadow(textRenderer, "Wykryto w lore: " + Fmt.money(detectedPrice) + " / szt.", px + 12, py + 56, GREEN);
        }

        c.drawTextWithShadow(textRenderer, "Maks cena (za sztukę):", px + 12, py + 88, GRAY);
        if (!error.isEmpty()) c.drawTextWithShadow(textRenderer, error, px + 12, py + 120, RED);

        super.render(c, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
