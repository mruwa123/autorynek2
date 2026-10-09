package pl.autorynek;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Czyste funkcje do czytania cen z lore ("Cena $125") i z pola "Maks $". Bez zależności od Minecrafta. */
public final class PriceParser {
    private PriceParser() {}

    /** Linia lore typu "Cena $125", "✕ Cena: $1.26K", "Cena 1 500" (bez spacji w liczbie). */
    private static final Pattern LINE =
            Pattern.compile("(?i)\\bcena\\b[^0-9\\n]{0,8}?([0-9][0-9.,]*)\\s*([kmb])?(?!\\p{L})");

    /** Pole tekstowe: "200", "$200", "1.5k", "2m". */
    private static final Pattern INPUT =
            Pattern.compile("(?i)^\\s*\\$?\\s*([0-9][0-9.,]*)\\s*([kmb])?\\s*$");

    /** @return cena z linii lore albo NaN, jeśli linia nie zawiera ceny. */
    public static double fromLine(String line) {
        if (line == null) return Double.NaN;
        Matcher m = LINE.matcher(line);
        if (!m.find()) return Double.NaN;
        return parseNumber(m.group(1), m.group(2) == null ? "" : m.group(2));
    }

    /** @return wartość z pola "Maks $" albo NaN, jeśli tekst jest niepoprawny. */
    public static double fromInput(String text) {
        if (text == null) return Double.NaN;
        Matcher m = INPUT.matcher(text);
        if (!m.matches()) return Double.NaN;
        return parseNumber(m.group(1), m.group(2) == null ? "" : m.group(2));
    }

    static double parseNumber(String raw, String suffix) {
        try {
            String s = raw.replaceAll("[.,]+$", "");
            if (s.isEmpty()) return Double.NaN;
            int lastDot = s.lastIndexOf('.');
            int lastComma = s.lastIndexOf(',');
            String norm;
            if (lastDot >= 0 && lastComma >= 0) {
                // oba separatory: ostatni to część dziesiętna
                int dec = Math.max(lastDot, lastComma);
                norm = s.substring(0, dec).replaceAll("[.,]", "") + "." + s.substring(dec + 1);
            } else if (lastDot >= 0 || lastComma >= 0) {
                int idx = Math.max(lastDot, lastComma);
                char sep = s.charAt(idx);
                long count = s.chars().filter(c -> c == sep).count();
                String after = s.substring(idx + 1);
                boolean thousands = count > 1
                        || (after.length() == 3 && suffix.isEmpty() && !s.startsWith("0"));
                norm = thousands ? s.replace(String.valueOf(sep), "") : s.replace(sep, '.');
            } else {
                norm = s;
            }
            double v = Double.parseDouble(norm);
            switch (suffix.toLowerCase()) {
                case "k" -> v *= 1_000d;
                case "m" -> v *= 1_000_000d;
                case "b" -> v *= 1_000_000_000d;
                default -> { }
            }
            return v;
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }
}
