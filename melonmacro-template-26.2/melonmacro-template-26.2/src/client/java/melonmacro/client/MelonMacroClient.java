package melonmacro.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class MelonMacroClient implements ClientModInitializer {

    private static final KeyMapping TOGGLE_MACRO = KeyMappingHelper.registerKeyMapping(
            new KeyMapping(
                    "key.melonmacro.toggle",
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_P,
                    KeyMapping.Category.MISC
            )
    );

    private static final KeyMapping TOGGLE_AUTOBREAK = KeyMappingHelper.registerKeyMapping(
            new KeyMapping(
                    "key.melonmacro.autobreak",
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_O,
                    KeyMapping.Category.MISC
            )
    );

    private static final KeyMapping CYCLE_DIRECTION = KeyMappingHelper.registerKeyMapping(
            new KeyMapping(
                    "key.melonmacro.cycledirection",
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_L,
                    KeyMapping.Category.MISC
            )
    );

    private static boolean macroEnabled = false;
    private static boolean autoBreakEnabled = false;

    // 0 = Forward, 1 = Right, 2 = Back, 3 = Left
    private static int directionIndex = 0;
    private static final String[] DIRECTION_NAMES = { "Forward", "Right", "Back", "Left" };

    // Fixed look direction while macro is on
    private static final float LOCKED_PITCH = -90.0F;
    private static final float LOCKED_YAW = 58.5F;

    public static boolean isMacroEnabled() {
        return macroEnabled;
    }

    @Override
    public void onInitializeClient() {

        ClientTickEvents.END_CLIENT_TICK.register(client -> {

            // --- Direction cycling ---
            while (CYCLE_DIRECTION.consumeClick()) {

                directionIndex = (directionIndex + 1) % 4;

                if (client.player != null) {
                    client.player.sendOverlayMessage(
                            Component.literal("Direction: " + DIRECTION_NAMES[directionIndex])
                    );

                    client.player.setDeltaMovement(
                            0.0,
                            client.player.getDeltaMovement().y,
                            0.0
                    );
                }
            }

            // --- Auto-walk toggle ---
            while (TOGGLE_MACRO.consumeClick()) {

                macroEnabled = !macroEnabled;

                if (client.player != null) {
                    client.player.sendOverlayMessage(
                            Component.literal(
                                    "Melon Macro: " +
                                            (macroEnabled ? "ON" : "OFF")
                            )
                    );

                    if (macroEnabled) {
                        client.player.setXRot(LOCKED_PITCH);
                        client.player.setYRot(LOCKED_YAW);
                    }
                }

                if (!macroEnabled && client.options != null) {
                    client.options.keyUp.setDown(false);
                    client.options.keyDown.setDown(false);
                    client.options.keyLeft.setDown(false);
                    client.options.keyRight.setDown(false);
                    client.options.keySprint.setDown(false);
                }
            }

            if (macroEnabled && client.options != null) {

                client.options.keyUp.setDown(directionIndex == 0);
                client.options.keyRight.setDown(directionIndex == 1);
                client.options.keyDown.setDown(directionIndex == 2);
                client.options.keyLeft.setDown(directionIndex == 3);
                client.options.keySprint.setDown(directionIndex == 0);

                if (client.player != null) {
                    client.player.setXRot(LOCKED_PITCH);
                    client.player.setYRot(LOCKED_YAW);
                }
            }

            // --- Auto-break toggle ---
            while (TOGGLE_AUTOBREAK.consumeClick()) {

                autoBreakEnabled = !autoBreakEnabled;

                if (client.player != null) {
                    client.player.sendOverlayMessage(
                            Component.literal(
                                    "Auto-Break: " +
                                            (autoBreakEnabled ? "ON" : "OFF")
                            )
                    );
                }

                if (!autoBreakEnabled && client.options != null) {
                    client.options.keyAttack.setDown(false);
                }
            }

            if (autoBreakEnabled && client.options != null) {
                client.options.keyAttack.setDown(true);
            }
        });
    }
}