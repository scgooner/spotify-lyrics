package dev.spotifylyrics;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import java.util.List;

/** Draws animated, karaoke-style lyrics straight onto the game view (HUD layer). */
public final class LyricsHud {
    public static boolean enabled = true;
    public static double offset = 0.0; // seconds; + shows lyrics later

    private static final Style STYLE = Style.EMPTY.withFont(
            new FontDescription.Resource(Identifier.fromNamespaceAndPath("spotifylyrics", "lyrics")));
    private static final float CURRENT_SCALE = 1.5f;
    private static final float SIDE_SCALE = 0.9f;

    private static String lastKey = "";
    private static int lastIdx = -2;
    private static long idxNanos = 0, trackNanos = 0;
    private static volatile String toast = "";
    private static volatile long toastNanos = 0;

    private LyricsHud() {}

    public static void toast(String s) {
        toast = s;
        toastNanos = System.nanoTime();
    }

    public static void render(GuiGraphicsExtractor g, DeltaTracker tick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null) return;
        long now = System.nanoTime();
        drawToast(g, mc.font, now);
        if (!enabled) return;

        String key = Now.key();
        if (key.isEmpty()) return;
        if (!key.equals(lastKey)) {
            lastKey = key;
            lastIdx = -2;
            trackNanos = now;
            idxNanos = now;
        }

        Font font = mc.font;
        int sw = g.guiWidth();
        int sh = g.guiHeight();
        float cx = sw / 2f;
        float y0 = sh - 100f;
        float global = Now.playing ? 1f : 0.45f;
        float time = now / 1e9f;
        float hue = (time * 0.05f) % 1f;

        drawTrackHeader(g, font, now, hue);

        List<LyricsManager.Line> lines = LyricsManager.lines();
        LyricsManager.Status st = LyricsManager.status();
        if (lines.isEmpty()) {
            String msg = st == LyricsManager.Status.LOADING ? "searching lyrics" + dots(time)
                    : st == LyricsManager.Status.MISSING ? "no synced lyrics found" : "";
            if (!msg.isEmpty()) draw(g, font, msg, cx, y0, SIDE_SCALE, 0.5f * global, hue, 0, false);
            return;
        }

        double pos = Now.position() + offset;
        int idx = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).time() <= pos) idx = i; else break;
        }
        if (idx != lastIdx) {
            lastIdx = idx;
            idxNanos = now;
        }

        float t = clamp((now - idxNanos) / 1e9f / 0.5f);
        float pop = easeOutBack(t);
        float fade = easeOutCubic(t);

        // previous line: slides up and shrinks
        if (idx > 0) {
            String prev = lines.get(idx - 1).text();
            float s = lerp(CURRENT_SCALE, SIDE_SCALE, fade);
            draw(g, font, shown(prev, time), cx, y0 - 18f * fade, s, (0.8f - 0.55f * fade) * global, hue, 0, false);
        }
        // next line: rises into view, dim
        if (idx + 1 < lines.size()) {
            String next = lines.get(idx + 1).text();
            draw(g, font, shown(next, time), cx, y0 + 20f + (1f - fade) * 10f, SIDE_SCALE, 0.35f * global, hue, 0, false);
        }
        // current line: pops in, fills with colour as it's sung
        if (idx >= 0) {
            double start = lines.get(idx).time();
            double end = idx + 1 < lines.size() ? lines.get(idx + 1).time() : start + 4;
            float prog = clamp((float) ((pos - start) / Math.max(0.3, Math.min(end - start, 6.0) * 0.9)));
            float s = CURRENT_SCALE * (0.85f + 0.15f * pop);
            draw(g, font, shown(lines.get(idx).text(), time), cx, y0 + (1f - fade) * 10f, s, fade * global, hue, prog, true);
        }
    }

    private static void drawToast(GuiGraphicsExtractor g, Font font, long now) {
        float age = (now - toastNanos) / 1e9f;
        if (toast.isEmpty() || age > 1.6f) return;
        float a = clamp((1.6f - age) / 0.4f);
        draw(g, font, toast, g.guiWidth() / 2f, 20f, 1f, a, 0f, 0, false);
    }

    private static void drawTrackHeader(GuiGraphicsExtractor g, Font font, long now, float hue) {
        float age = (now - trackNanos) / 1e9f;
        float total = 6f;
        if (age > total) return;
        float in = easeOutCubic(clamp(age / 0.6f));
        float out = clamp((total - age) / 0.6f);
        float a = Math.min(in, out);
        float x = lerp(-120f, 12f, in) - (1f - out) * 40f;
        int al = (int) (a * 255);
        if (al <= 4) return;
        int rgb = Mth.hsvToRgb(hue, 0.55f, 1f) & 0xFFFFFF;

        g.pose().pushMatrix();
        g.pose().translate(x, 12f);
        g.pose().scale(1.2f, 1.2f);
        g.text(font, text(Now.title), 0, 0, (al << 24) | rgb, true);
        g.pose().scale(0.75f, 0.75f);
        g.text(font, text(Now.artist), 0, 14, (al << 24) | 0xCCCCCC, true);
        g.pose().popMatrix();
    }

    private static void draw(GuiGraphicsExtractor g, Font font, String s, float cx, float y, float scale,
                             float alpha, float hue, float progress, boolean highlight) {
        int al = (int) (clamp(alpha) * 255);
        if (al <= 4 || s.isEmpty()) return;
        MutableComponent full = text(s);
        int w = font.width(full);
        int rgbLit = Mth.hsvToRgb(hue, 0.55f, 1f) & 0xFFFFFF;
        int rgbDim = 0xBBBBBB;

        g.pose().pushMatrix();
        g.pose().translate(cx, y);
        g.pose().scale(scale, scale);
        int x = (int) (-w / 2f);
        if (highlight) {
            int n = Math.max(0, Math.min(s.length(), Math.round(s.length() * progress)));
            MutableComponent a = text(s.substring(0, n));
            MutableComponent b = text(s.substring(n));
            g.text(font, a, x, 0, (al << 24) | rgbLit, true);
            g.text(font, b, x + font.width(a), 0, (al << 24) | rgbDim, true);
        } else {
            g.text(font, full, x, 0, (al << 24) | rgbDim, true);
        }
        g.pose().popMatrix();
    }

    private static String shown(String s, float time) {
        return s.isBlank() ? dots(time) : s;
    }

    private static String dots(float time) {
        int n = 1 + (int) ((time * 2f) % 3f);
        return ". ".repeat(n).trim();
    }

    private static MutableComponent text(String s) { return Component.literal(s).setStyle(STYLE); }

    private static float clamp(float v) { return Math.max(0f, Math.min(1f, v)); }
    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
    private static float easeOutCubic(float t) { float u = 1f - t; return 1f - u * u * u; }
    private static float easeOutBack(float t) {
        float c1 = 1.70158f, c3 = c1 + 1f;
        float u = t - 1f;
        return 1f + c3 * u * u * u + c1 * u * u;
    }
}
