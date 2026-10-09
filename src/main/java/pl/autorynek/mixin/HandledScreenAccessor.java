package pl.autorynek.mixin;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Udostępnia slot pod kursorem (potrzebny do Ctrl+A na przedmiocie w rynku). */
@Mixin(HandledScreen.class)
public interface HandledScreenAccessor {
    @Accessor("focusedSlot")
    Slot getFocusedSlot();
}
