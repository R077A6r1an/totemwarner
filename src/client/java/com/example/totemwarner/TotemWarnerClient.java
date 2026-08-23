package com.example.totemwarner;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Items;

/**
 * Mojang-mappings (Mojmap) build for Minecraft 26.1.2.
 * As of 26.1.2 the Fabric default switched from Yarn to official Mojang names.
 */
public class TotemWarnerClient implements ClientModInitializer {

	// Match this to the id in your fabric.mod.json.
	public static final String MOD_ID = "totem-warner";

	// ----- Tunable knobs -----------------------------------------------------
	private static final long REMINDER_SOUND_INTERVAL_MS = 1500; // gentle nag
	private static final long CRITICAL_SOUND_INTERVAL_MS = 700;  // urgent nag
	// -------------------------------------------------------------------------

	private static KeyMapping dismissKey;

	// Used to detect entering a world (per-session reset without extra events).
	private boolean hadPlayer = false;

	@Override
	public void onInitializeClient() {
		// 26.1: key mappings take a registered Category object, not a String.
		KeyMapping.Category category = KeyMapping.Category.register(
				Identifier.fromNamespaceAndPath(MOD_ID, "main"));

		dismissKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.totemwarner.dismiss",
				InputConstants.Type.KEYSYM,
				InputConstants.KEY_V,
				category
		));

		ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);

		// 26.1 HUD API: HudRenderCallback was removed. Register a HudElement that
		// renders just before the chat layer; the API handles z-ordering.
		HudElementRegistry.attachElementBefore(
				VanillaHudElements.CHAT,
				Identifier.fromNamespaceAndPath(MOD_ID, "totem_warning"),
				TotemWarnerHud::render);
	}

	private void onClientTick(Minecraft client) {
		LocalPlayer player = client.player;

		// Reset once per session: fires on the transition from no-player
		// (menu / disconnected) to in-world, i.e. joining a world or server.
		boolean hasPlayer = player != null;
		if (hasPlayer && !hadPlayer) {
			WarnerState.resetForNewSession();
		}
		hadPlayer = hasPlayer;
		if (!hasPlayer) {
			return;
		}

		// The dismiss key may have queued one or more presses since last tick.
		while (dismissKey.consumeClick()) {
			WarnerState.dismissedLevel = WarnerState.level;
		}

		// getContainerSize()/getItem() span main inventory, armor AND offhand.
		Inventory inv = player.getInventory();
		boolean totemAnywhere = false;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (inv.getItem(i).is(Items.TOTEM_OF_UNDYING)) {
				totemAnywhere = true;
				break;
			}
		}
		boolean totemInOffhand = player.getOffhandItem().is(Items.TOTEM_OF_UNDYING);

		// Arm the warner the first moment a totem is ever seen this session.
		if (!WarnerState.activated && totemAnywhere) {
			WarnerState.activated = true;
		}

		// Decide the current warning level.
		WarnerState.Level newLevel;
		if (!WarnerState.activated) {
			newLevel = WarnerState.Level.NONE;            // never had a totem yet
		} else if (totemInOffhand) {
			newLevel = WarnerState.Level.NONE;            // all good
		} else if (totemAnywhere) {
			newLevel = WarnerState.Level.REMINDER;        // have one, not in offhand
		} else {
			newLevel = WarnerState.Level.CRITICAL;        // none at all
		}

		// On any severity change, re-arm (clear dismissal) and allow an instant alert.
		if (newLevel != WarnerState.level) {
			WarnerState.level = newLevel;
			WarnerState.dismissedLevel = WarnerState.Level.NONE;
			WarnerState.lastSoundMs = 0L;
		}

		// Periodic alert sound, unless the player has silenced this level.
		if (WarnerState.level != WarnerState.Level.NONE && !WarnerState.isSuppressed()) {
			long now = System.currentTimeMillis();
			boolean critical = WarnerState.level == WarnerState.Level.CRITICAL;
			long interval = critical ? CRITICAL_SOUND_INTERVAL_MS : REMINDER_SOUND_INTERVAL_MS;
			if (now - WarnerState.lastSoundMs >= interval) {
				WarnerState.lastSoundMs = now;
				if (critical) {
					playSound(SoundEvents.NOTE_BLOCK_BASS, 0.5f, 1.0f);  // low + loud
				} else {
					playSound(SoundEvents.NOTE_BLOCK_PLING, 1.5f, 0.8f); // high ping
				}
			}
		}
	}

	// forUI(sound, pitch, volume). The Holder is accepted directly in 26.1.
	public static void playSound(Holder<SoundEvent> sound, float pitch, float volume) {
		Minecraft.getInstance().getSoundManager()
				.play(SimpleSoundInstance.forUI(sound.value(), pitch, volume));
	}
}