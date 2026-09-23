package org.r077a6r1an.totemwarner;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * HUD overlay for 26.1.2. Registered via HudElementRegistry, so the render method
 * matches the HudElement signature: (GuiGraphicsExtractor, DeltaTracker).
 *
 * Two levels: REMINDER (amber) and CRITICAL (red). A pulsing screen-edge border
 * plus a banner, faster/stronger when critical. Dismissing hides it (and mutes
 * the nag sound) until the severity changes.
 */
public final class TotemWarnerHud {
    private TotemWarnerHud() {}

    private static final int BORDER = 8; // border thickness in px

    public static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) {
            return;
        }
        if (WarnerState.level == WarnerState.Level.NONE || WarnerState.isSuppressed()) {
            return;
        }

        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        long now = System.currentTimeMillis();
        boolean critical = WarnerState.level == WarnerState.Level.CRITICAL;

        // Pulse the alpha with a sine wave: stronger and faster when critical.
        double speed = critical ? 0.009 : 0.0045;
        double pulse = (Math.sin(now * speed) + 1.0) / 2.0;     // 0..1
        int minA = critical ? 95 : 55;
        int maxA = critical ? 225 : 150;
        int alpha = (int) (minA + (maxA - minA) * pulse) & 0xFF;
        int rgb = critical ? 0xFF1414 : 0xFFB000;               // red vs amber
        int color = (alpha << 24) | rgb;                        // ARGB

        // Four-rectangle border.
        graphics.fill(0, 0, w, BORDER, color);            // top
        graphics.fill(0, h - BORDER, w, h, color);        // bottom
        graphics.fill(0, 0, BORDER, h, color);            // left
        graphics.fill(w - BORDER, 0, w, h, color);        // right

        // Banner near the top.
        Font font = mc.font;
        String head = critical ? "!!! OUT OF TOTEMS !!!" : "!! NO TOTEM IN OFFHAND";
        String sub  = "press [V] to silence";

        int boxW = Math.max(font.width(head), font.width(sub)) + 16;
        int boxH = 28;
        int bx = (w - boxW) / 2;
        int by = BORDER + 6;
        int boxA = ((int) (140 * pulse) + 70) & 0xFF;
        graphics.fill(bx, by, bx + boxW, by + boxH,
                (boxA << 24) | (critical ? 0x400000 : 0x402800));

        // NOTE: text color must be ARGB in 26.1 — an RGB value renders invisible.
        drawCentered(graphics, font, head, w / 2, by + 5,  0xFFFFFFFF);
        drawCentered(graphics, font, sub,  w / 2, by + 16, 0xFFCFCFCF);
    }

    // 26.1 replaces drawCenteredString with a unified text(...) method, so we
    // center manually: text(font, string, x, y, argbColor, dropShadow).
    private static void drawCentered(GuiGraphicsExtractor g, Font font, String text,
                                     int centerX, int y, int argb) {
        int x = centerX - font.width(text) / 2;
        g.text(font, text, x, y, argb, true);
    }
}