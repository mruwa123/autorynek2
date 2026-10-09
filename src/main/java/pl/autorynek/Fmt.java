package pl.autorynek;

import java.util.Locale;

/** Formatowanie kwot i czasu do GUI / HUD. */
public final class Fmt {
    private Fmt() {}

    private static final Locale PL = Locale.forLanguageTag("pl-PL");

    /** "$0", "$125", "$12,50" */
    public static String money(double v) {
        if (Math.abs(v - Math.rint(v)) < 0.005) return "$" + (long) Math.rint(v);
        return "$" + String.format(PL, "%.2f", v);
    }

    /** Zawsze dwa miejsca po przecinku: "$200,00" */
    public static String money2(double v) {
        return "$" + String.format(PL, "%.2f", v);
    }

    public static String time(long millis) {
        long s = Math.max(0, millis / 1000);
        return String.format("%02d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60);
    }
}
