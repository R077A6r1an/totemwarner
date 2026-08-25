package com.example.totemwarner;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * HUD overlay (Mojmap, 1.21.11).
 *
 * Two persistent levels (REMINDER amber, CRITICAL red) render a pulsing border
 * plus a banner; the pulse is faster/stronger for CRITICAL so it registers in
 * peripheral vision while you concentrate. A separate brief full-screen red flash
 * fires whenever a crystal hit is blocked. Pressing the dismiss key hides the
 * persistent overlay (and mutes the nag sound) until the severity changes.
 */
public final class TotemWarnerHud {
    private TotemWarnerHud() {}

    private static final int BORDER = 8; // border thickness in px
    private static final long CRYSTAL_FLASH_MS = 1200;

    // Mojmap HudRenderCallback signature: (GuiGraphics, DeltaTracker).
    public static void render(GuiGraphics graphics, DeltaTracker tickCounter) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) {
            return;
        }

        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        long now = System.currentTimeMillis();

        // --- Transient crystal-block flash (always shows; it is a safety signal) ---
        long sinceBlock = now - WarnerState.lastCrystalBlockMs;
        if (WarnerState.lastCrystalBlockMs > 0 && sinceBlock < CRYSTAL_FLASH_MS) {
            float t = 1.0f - (sinceBlock / (float) CRYSTAL_FLASH_MS); // 1 -> 0
            int a = (int) (150 * t) & 0xFF;
            graphics.fill(0, 0, w, h, (a << 24) | 0xFF0000);
        }

        // --- Persistent warning overlay ---
        if (WarnerState.level == WarnerState.Level.NONE || WarnerState.isSuppressed()) {
            return;
        }
        boolean critical = WarnerState.level == WarnerState.Level.CRITICAL;

        // Pulse the alpha with a sine wave: stronger and faster when critical.
        double speed = critical ? 0.009 : 0.0045;
        double pulse = (Math.sin(now * speed) + 1.0) / 2.0;     // 0..1
        int minA = critical ? 95 : 55;
        int maxA = critical ? 225 : 150;
        int alpha = (int) (minA + (maxA - minA) * pulse) & 0xFF;
        int rgb = critical ? 0xFF1414 : 0xFFB000;               // red vs amber
        int color = (alpha << 24) | rgb;

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

        graphics.drawCenteredString(font, Component.literal(head), w / 2, by + 5,  0xFFFFFFFF);
        graphics.drawCenteredString(font, Component.literal(sub),  w / 2, by + 16, 0xFFCFCFCF);
    }
}