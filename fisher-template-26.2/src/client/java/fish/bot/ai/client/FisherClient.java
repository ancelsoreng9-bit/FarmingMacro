package com.example.fishingai;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
public class FishingAIClient implements ClientModInitializer {
	private static boolean aiEnabled = false;
	private static KeyMapping toggleKey;
	private static final KeyMapping.Category CATEGORY =
			KeyMapping.Category.register(
					Identifier.fromNamespaceAndPath("fishingai", "main"));
	@Override
	public void onInitializeClient() {
		toggleKey = KeyMappingHelper.registerKeyMapping(
				new KeyMapping("key.fishingai.toggle",
						InputConstants.Type.KEYSYM,
						GLFW.GLFW_KEY_G, CATEGORY));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (toggleKey.consumeClick()) {
				aiEnabled = !aiEnabled;
				if (client.player != null) {
					client.player.displayClientMessage(
							Component.literal("Fishing AI: "
									+ (aiEnabled ? "ON" : "OFF")), true);
				}
			}
			if (aiEnabled && client.player != null
					&& client.level != null) {
				runAI(client);
			}
		});
	}
	private void runAI(Minecraft client) {
		// TODO: observe state, choose action, execute safely
	}
	public static boolean isAIEnabled() { return aiEnabled; }
}
