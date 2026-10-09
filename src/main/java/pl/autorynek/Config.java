package pl.autorynek;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Ustawienia zapisywane w .minecraft/config/autorynek.json */
public final class Config {

    public enum State { UNKNOWN, GOOD, TOO_EXPENSIVE, NO_PRICE }

    public static final class Entry {
        public String name;
        public double maxPrice;
        /** np. "minecraft:ghast_tear" - tylko do narysowania ikonki na liście */
        public String itemId;
        /** ostatnio zaobserwowany stan oferty (kolor ceny na liście) */
        public transient State state = State.UNKNOWN;

        public boolean matches(String stackName) {
            if (name == null || name.isBlank() || stackName == null) return false;
            return normalize(stackName).contains(normalize(name));
        }
    }

    public List<Entry> items = new ArrayList<>();
    /** odstęp między kliknięciami w oferty (ms) */
    public int clickMs = 150;
    /** co ile ms odświeżać rynek (ms) */
    public int scanMs = 600;
    public String marketCommand = "/ah open";
    /** false = "Pojedynczo" (jeden zakup na widok rynku), true = "Wszystkie" (kupuj każdą pasującą ofertę) */
    public boolean buyAll = false;
    /** numer slotu przycisku odświeżania w rynku; -1 = zamknij i otwórz rynek komendą od nowa */
    public int refreshSlot = -1;
    /** numer slotu przycisku "Potwierdź" w oknie potwierdzenia zakupu; -1 = szukaj automatycznie po nazwie */
    public int confirmSlot = -1;
    /** wzorce wiadomości czatu rozpoznające udany zakup / przegapienie (do statystyk na HUD) */
    public String buyPattern = "(?i)(kupi[łl]e[śs]|zakupi[łl]e[śs]|pomy[śs]lnie|zakupiono|kupiono)";
    public String missPattern = "(?i)(kto[śs] (ci )?(ubieg|wyprzed)|ju[żz] sprzedan|nie jest ju[żz]|nie istnieje|niedost[ęe]pn)";

    // ---------------------------------------------------------------- singleton / IO

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Config instance;

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("autorynek.json");
    }

    public static synchronized Config get() {
        if (instance == null) instance = load();
        return instance;
    }

    private static Config load() {
        Config c = null;
        try {
            Path f = file();
            if (Files.exists(f)) c = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), Config.class);
        } catch (Exception e) {
            System.err.println("[AutoRynek] Nie udało się wczytać konfiguracji: " + e);
        }
        if (c == null) c = new Config();
        if (c.items == null) c.items = new ArrayList<>();
        c.items.removeIf(e -> e == null || e.name == null || e.name.isBlank());
        c.clickMs = Math.max(50, c.clickMs);
        c.scanMs = Math.max(100, c.scanMs);
        if (c.marketCommand == null || c.marketCommand.isBlank()) c.marketCommand = "/ah open";
        if (c.buyPattern == null) c.buyPattern = new Config().buyPattern;
        if (c.missPattern == null) c.missPattern = new Config().missPattern;
        return c;
    }

    public synchronized void save() {
        try {
            Files.writeString(file(), GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("[AutoRynek] Nie udało się zapisać konfiguracji: " + e);
        }
    }

    // ---------------------------------------------------------------- lista skupu

    /** Dodaje wpis albo aktualizuje cenę istniejącego (po nazwie, bez względu na wielkość liter). */
    public void upsert(String name, double maxPrice, String itemId) {
        for (Entry e : items) {
            if (normalize(e.name).equals(normalize(name))) {
                e.maxPrice = maxPrice;
                if (itemId != null) e.itemId = itemId;
                return;
            }
        }
        Entry e = new Entry();
        e.name = name;
        e.maxPrice = maxPrice;
        e.itemId = itemId;
        items.add(e);
    }

    /** Najbardziej szczegółowy (najdłuższa nazwa) wpis pasujący do nazwy przedmiotu z rynku. */
    public Entry find(String stackName) {
        Entry best = null;
        for (Entry e : items) {
            if (e.matches(stackName) && (best == null || e.name.length() > best.name.length())) best = e;
        }
        return best;
    }

    public static String normalize(String s) {
        if (s == null) return "";
        String lower = s.toLowerCase(Locale.ROOT).replace('ł', 'l');
        return Normalizer.normalize(lower, Normalizer.Form.NFD).replaceAll("\\p{M}+", "").trim();
    }
}
