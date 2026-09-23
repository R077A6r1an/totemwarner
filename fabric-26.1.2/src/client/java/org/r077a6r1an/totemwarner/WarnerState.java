package org.r077a6r1an.totemwarner;

/**
 * Shared, session-scoped state for the Totem Warner.
 *
 * Everything here is static because there is only ever one client. The state is
 * reset whenever you join a world or server (see TotemWarnerClient), which is what
 * makes the offhand reminder "activate once you first get a totem" on a per-session
 * basis.
 */
public final class WarnerState {
    private WarnerState() {}

    public enum Level { NONE, REMINDER, CRITICAL }

    /** Becomes true the first time a totem is seen in the inventory this session. */
    public static boolean activated = false;

    /** Current computed warning level. */
    public static Level level = Level.NONE;

    /**
     * The level the player has chosen to silence with the dismiss key. While this
     * equals the current level, the visual + audio nag is suppressed so the player
     * can keep fighting. It is automatically cleared on any severity change, so a
     * new or escalated warning always re-asserts itself.
     */
    public static Level dismissedLevel = Level.NONE;

    /** Timestamp (ms) of the last blocked crystal hit, drives the red flash. */
    public static long lastCrystalBlockMs = 0L;

    /** Last time the periodic alert sound played. */
    public static long lastSoundMs = 0L;

    public static void resetForNewSession() {
        activated = false;
        level = Level.NONE;
        dismissedLevel = Level.NONE;
        lastCrystalBlockMs = 0L;
        lastSoundMs = 0L;
    }

    /** True when the current persistent warning has been dismissed by the player. */
    public static boolean isSuppressed() {
        return level != Level.NONE && level == dismissedLevel;
    }
}