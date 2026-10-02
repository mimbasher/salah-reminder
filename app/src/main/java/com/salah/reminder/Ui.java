package com.salah.reminder;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** The app's small design system: one place for colour, type, spacing and the common views. */
final class Ui {
    /** True while the phone is in dark mode; the palette below follows it. */
    static boolean dark = true;

    static int BG, SURFACE, SURFACE_2, LINE, TEXT, MUTED;
    static int ACCENT, ACCENT_A, ACCENT_B, ON_ACCENT;
    static int GOLD, AMBER, WARN_TEXT, RIPPLE;

    /** The hero card keeps its deep green in both themes; white text sits on it either way. */
    static final int HERO_A = 0xFF0E6650;
    static final int HERO_B = 0xFF123F62;
    static final int ON_HERO = 0xFFFFFFFF;
    static final int ON_HERO_2 = 0xB3FFFFFF;

    static final int THEME_SYSTEM = 0, THEME_LIGHT = 1, THEME_DARK = 2;
    static final String[] THEME_NAMES = {"System", "Light", "Dark"};

    static final Typeface LIGHT  = Typeface.create("sans-serif-light", Typeface.NORMAL);
    static final Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);

    static { palette(true); }

    /**
     * Reads the system's light/dark setting and switches the palette. Every activity calls this
     * first thing in onCreate; Android recreates activities when the setting changes, so the
     * colours always match what the phone is showing.
     */
    static void apply(Context c) {
        palette((c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES);
    }

    /** Which of the three theme settings the user picked. */
    static int themeChoice(Context c) {
        int t = Scheduler.prefs(c).getInt("theme", THEME_SYSTEM);
        return t < 0 || t > 2 ? THEME_SYSTEM : t;
    }

    /** Whether the app should be dark right now, honouring the setting over the system. */
    static boolean nightWanted(Context c) {
        switch (themeChoice(c)) {
            case THEME_LIGHT: return false;
            case THEME_DARK: return true;
            default:
                return (c.getResources().getConfiguration().uiMode
                        & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        }
    }

    private static void palette(boolean night) {
        dark = night;
        if (night) {
            BG = 0xFF070D16;  SURFACE = 0xFF121B29;  SURFACE_2 = 0xFF1B2636;
            LINE = 0xFF26354A; TEXT = 0xFFEEF3F9;    MUTED = 0xFF8798AE;
            ACCENT = 0xFF34D8A5; ACCENT_A = 0xFF3FE3AE; ACCENT_B = 0xFF22B98A;
            ON_ACCENT = 0xFF04231B;
            GOLD = 0xFFF2C879; AMBER = 0xFFFFB454; WARN_TEXT = 0xFFFFD79A;
            RIPPLE = 0x40FFFFFF;
        } else {
            BG = 0xFFF4F6F9;  SURFACE = 0xFFFFFFFF;  SURFACE_2 = 0xFFEDF1F6;
            LINE = 0xFFDCE3EB; TEXT = 0xFF101B28;    MUTED = 0xFF5B6B7D;
            ACCENT = 0xFF0C7B5B; ACCENT_A = 0xFF13A378; ACCENT_B = 0xFF0A6E51;
            ON_ACCENT = 0xFFFFFFFF;
            GOLD = 0xFFAE7612; AMBER = 0xFFB4690A; WARN_TEXT = 0xFF8A4F00;
            RIPPLE = 0x1F000000;
        }
    }

    private Ui() { }

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    // ---------------- backgrounds ----------------

    static GradientDrawable round(int color, Context c, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.RECTANGLE);
        g.setColor(color);
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    static GradientDrawable outlined(int color, int stroke, Context c, float radiusDp) {
        GradientDrawable g = round(color, c, radiusDp);
        g.setStroke(dp(c, 1), stroke);
        return g;
    }

    static GradientDrawable gradient(Context c, int from, int to, float radiusDp) {
        GradientDrawable g = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR, new int[]{from, to});
        g.setShape(GradientDrawable.RECTANGLE);
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    static GradientDrawable circle(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        return g;
    }

    static GradientDrawable circleOutline(Context c, int color, int stroke) {
        GradientDrawable g = circle(color);
        g.setStroke(dp(c, 1), stroke);
        return g;
    }

    /** Wraps a background so taps show a ripple. */
    static Drawable tappable(Drawable bg) {
        return new RippleDrawable(ColorStateList.valueOf(RIPPLE), bg, null);
    }

    static void clickable(View v, Drawable bg, View.OnClickListener l) {
        v.setBackground(tappable(bg));
        v.setClickable(true);
        v.setFocusable(true);
        v.setOnClickListener(l);
    }

    // ---------------- text ----------------

    static TextView text(Context c, String s, float sizeSp, int color, Typeface tf) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sizeSp);
        t.setTextColor(color);
        if (tf != null) t.setTypeface(tf);
        return t;
    }

    /** Small all-caps label with wide tracking, used above every block. */
    static TextView overline(Context c, String s, int color) {
        TextView t = text(c, s.toUpperCase(), 11, color, MEDIUM);
        t.setLetterSpacing(0.16f);
        return t;
    }

    static TextView sectionTitle(Context c, String s) {
        TextView t = overline(c, s, MUTED);
        t.setPadding(dp(c, 4), dp(c, 22), 0, dp(c, 8));
        return t;
    }

    // ---------------- containers ----------------

    static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    static LinearLayout rowOf(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    /** A rounded surface panel. */
    static LinearLayout card(Context c) {
        LinearLayout l = column(c);
        l.setBackground(round(SURFACE, c, 20));
        l.setPadding(dp(c, 16), dp(c, 16), dp(c, 16), dp(c, 16));
        return l;
    }

    static LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    static LinearLayout.LayoutParams lp(int w, int h, float weight) {
        return new LinearLayout.LayoutParams(w, h, weight);
    }

    static LinearLayout.LayoutParams stacked(Context c, float topDp) {
        LinearLayout.LayoutParams p = lp(-1, -2);
        p.topMargin = dp(c, topDp);
        return p;
    }

    static View spacerH(Context c) {
        View v = new View(c);
        v.setLayoutParams(lp(0, 1, 1));
        return v;
    }

    static View divider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(LINE);
        LinearLayout.LayoutParams p = lp(-1, Math.max(1, dp(c, 0.7f)));
        p.topMargin = dp(c, 12);
        p.bottomMargin = dp(c, 4);
        v.setLayoutParams(p);
        return v;
    }

    // ---------------- buttons & inputs ----------------

    /** Solid accent-gradient call to action. */
    static TextView primary(Context c, String label, View.OnClickListener l) {
        TextView t = text(c, label, 16, ON_ACCENT, MEDIUM);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(c, 18), dp(c, 15), dp(c, 18), dp(c, 15));
        clickable(t, gradient(c, ACCENT_A, ACCENT_B, 16), l);
        return t;
    }

    /** Quiet outlined button. */
    static TextView secondary(Context c, String label, View.OnClickListener l) {
        TextView t = text(c, label, 15, TEXT, MEDIUM);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(c, 14), dp(c, 13), dp(c, 14), dp(c, 13));
        clickable(t, outlined(SURFACE_2, LINE, c, 14), l);
        return t;
    }

    /** Round icon button for the back arrow, the gear and the like. */
    static ImageView iconButton(Context c, int drawable, String description, View.OnClickListener l) {
        ImageView v = new ImageView(c);
        v.setImageResource(drawable);
        v.setImageTintList(ColorStateList.valueOf(TEXT));
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        v.setContentDescription(description);
        int pad = dp(c, 11);
        v.setPadding(pad, pad, pad, pad);
        int size = dp(c, 44);
        v.setLayoutParams(lp(size, size));
        clickable(v, circle(SURFACE), l);
        return v;
    }

    static EditText input(Context c, String hint) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setSingleLine(true);
        e.setTextSize(15);
        e.setTextColor(TEXT);
        e.setHintTextColor(MUTED);
        e.setBackground(outlined(SURFACE_2, LINE, c, 14));
        e.setPadding(dp(c, 14), dp(c, 13), dp(c, 14), dp(c, 13));
        return e;
    }

    /** A selectable pill, used for the minute pickers. */
    static TextView chip(Context c, String label, boolean on, View.OnClickListener l) {
        TextView t = text(c, label, 14, on ? ON_ACCENT : TEXT, MEDIUM);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(c, 16), dp(c, 10), dp(c, 16), dp(c, 10));
        clickable(t, on ? round(ACCENT, c, 22) : outlined(SURFACE_2, LINE, c, 22), l);
        return t;
    }

    /** A circular glyph badge, used for each prayer in the list. */
    static TextView badge(Context c, String glyph, int bg, int fg, int sizeDp) {
        TextView t = text(c, glyph, sizeDp * 0.42f, fg, null);
        t.setGravity(Gravity.CENTER);
        t.setBackground(circle(bg));
        t.setLayoutParams(lp(dp(c, sizeDp), dp(c, sizeDp)));
        return t;
    }

    /** Fades a colour towards transparent, for tinted bubbles on dark surfaces. */
    static int alpha(int color, int a) {
        return (color & 0x00FFFFFF) | (a << 24);
    }

    static int blend(int color, int onto, float amount) {
        return Color.rgb(
                Math.round(Color.red(onto) + (Color.red(color) - Color.red(onto)) * amount),
                Math.round(Color.green(onto) + (Color.green(color) - Color.green(onto)) * amount),
                Math.round(Color.blue(onto) + (Color.blue(color) - Color.blue(onto)) * amount));
    }
}
