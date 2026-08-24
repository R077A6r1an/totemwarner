package com.example.totemwarner;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

/**
 * Mojang-mappings (Mojmap) build for Minecraft 1.21.11.
 * As of 1.21.11 the Fabric default switched from Yarn to official Mojang names.
 */
public class TotemWarnerClient implements ClientModInitializer {

	// ----- Tunable knobs -----------------------------------------------------
	private static final long REMINDER_SOUND_INTERVAL_MS = 1500; // gentle nag
	private static final long CRITICAL_SOUND_INTERVAL_MS = 700;  // urgent nag
	// -------------------------------------------------------------------------

	private static KeyMapping dismissKey;

	@Override
	public void onInitializeClient() {
		// Dismiss / snooze key. Silences the CURRENT warning until its severity
		// changes, so you can keep playing in a clutch without the overlay.
		dismissKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.totemwarner.dismiss",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_V,
				KeyMapping.Category.GAMEPLAY
		));

		// Reset per server / per world: the offhand reminder only arms after you
		// pick up your first totem of the session.
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> WarnerState.resetForNewSession());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> WarnerState.resetForNewSession());

		ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);

		// HudRenderCallback is deprecated (replaced by HudElementRegistry) but is
		// still present and works in 1.21.11. See README for the modern migration.
		HudRenderCallback.EVENT.register(TotemWarnerHud::render);
	}

	private void onClientTick(Minecraft client) {
		LocalPlayer player = client.player;
		if (player == null) {
			return;
		}

		// The dismiss key may have queued one or more presses since last tick.
		while (dismissKey.consumeClick()) {
			WarnerState.dismissedLevel = WarnerState.level;
		}

		// getContainerSize()/getItem() span main inventory, armor AND offhand,
		// so "totem anywhere" really means anywhere.
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
					playSound(SoundEvents.NOTE_BLOCK_BASS, 1.0f, 0.5f);
				} else {
					playSound(SoundEvents.NOTE_BLOCK_PLING, 0.8f, 1.5f);
				}
			}
		}
	}

	public static void playSound(Holder<SoundEvent> sound, float volume, float pitch) {
		// SimpleSoundInstance.forUI args are (sound, pitch, volume).
		Minecraft.getInstance().getSoundManager()
				.play(SimpleSoundInstance.forUI(sound.value(), pitch, volume));
	}
}