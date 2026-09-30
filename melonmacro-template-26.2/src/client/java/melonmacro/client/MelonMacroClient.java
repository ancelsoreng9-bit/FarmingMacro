package melonmacro.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundPlayerAbilitiesPacket;
import net.minecraft.world.level.block.state.BlockState;
import org.lwjgl.glfw.GLFW;

import java.util.LinkedHashMap;
import java.util.Map;

public class MelonMacroClient implements ClientModInitializer {

    public enum FarmType {
        MELON(-58.5F, -90.0F),
        COCOA(-58.0F, -180.0F),
        SUGARCANE(0.0F, -50.0F),
        PCBM(0.0F, -90.0F);

        public final float targetPitch;
        public final float targetYaw;

        FarmType(float targetPitch, float targetYaw) {
            this.targetPitch = targetPitch;
            this.targetYaw = targetYaw;
        }
    }

    private static FarmType currentFarm = FarmType.MELON;
    private static boolean macroEnabled = false;
    private static boolean autoBreakEnabled = false;

    // --- Keybindings ---
    private static final KeyMapping SWITCH_MACRO = KeyMappingHelper.registerKeyMapping(
            new KeyMapping("key.melonmacro.switch", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_I, KeyMapping.Category.MISC)
    );
    private static final KeyMapping TOGGLE_MACRO = KeyMappingHelper.registerKeyMapping(
            new KeyMapping("key.melonmacro.toggle", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_P, KeyMapping.Category.MISC)
    );
    private static final KeyMapping TOGGLE_AUTOBREAK = KeyMappingHelper.registerKeyMapping(
            new KeyMapping("key.melonmacro.autobreak", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_O, KeyMapping.Category.MISC)
    );

    // --- Movement Sequences ---
    // Directions: 0 = Forward, 1 = Right, 2 = Backward, 3 = Left

    private static final int[] MELON_ZIGZAG = { 0, 1, 0, 3 };
    private static final String[] MELON_NAMES = { "Forward", "Right", "Forward", "Left" };

    // Confirmed: this pattern belongs to SUGARCANE
    private static final int[] SUGARCANE_SEQUENCE = { 3, 0, 3, 2 };
    private static final String[] SUGARCANE_NAMES = { "Left", "Forward", "Left", "Backward" };

    // Placeholder pattern for COCOA — tell me if this needs to be different
    private static final int[] COCOA_SEQUENCE = { 0, 1, 0, 3 };
    private static final String[] COCOA_NAMES = { "Forward", "Right", "Forward", "Left" };

    // Placeholder pattern for PCBM — tell me if this needs to be different
    private static final int[] PCBM_SEQUENCE = { 0, 1, 0, 3 };
    private static final String[] PCBM_NAMES = { "Forward", "Right", "Forward", "Left" };

    // --- Tunable Constants ---
    private static final double MOVE_EPSILON = 0.01;
    private static final int STUCK_TICKS_THRESHOLD = 15;
    private static final int MAX_IDLE_TICKS = 100; // exactly 5 seconds
    private static final int POLL_INTERVAL_TICKS = 5;

    private static final int INITIAL_WAIT_DURATION = 40;   // 2 seconds
    private static final int HOVER_SYNC_DURATION = 30;     // 1.5 seconds
    private static final int TOUCHDOWN_SYNC_DURATION = 25; // ~1.25 seconds
    private static final int DOUBLE_TAP_CONFIRM_TICKS = 12;

    // Hard timeouts so phases can never freeze the macro
    private static final int PHASE_TIMEOUT = 200; // 10 seconds max per phase

    // --- State Variables ---
    private static int zigzagStep = 0;
    private static int stuckTicks = 0;
    private static int ticksSinceLastBreak = 0;
    private static int globalTickCounter = 0;
    private static double lastX = 0.0;
    private static double lastZ = 0.0;
    private static int lastTotalItemCount = -1;

    private static boolean isReturning = false;
    private static int returnPhase = 0;
    private static int waitTicks = 0;
    private static int phaseTicks = 0;
    private static double returnTargetY = 0.0;
    private static double startX = 0.0;
    private static double startY = 0.0;
    private static double startZ = 0.0;

    private static final Map<BlockPos, BlockState> nearbyBlockCache = new LinkedHashMap<BlockPos, BlockState>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<BlockPos, BlockState> eldest) {
            return size() > 250;
        }
    };

    private static final BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

    public static boolean isMacroEnabled() {
        return macroEnabled;
    }

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
    }

    private void onClientTick(Minecraft client) {
        if (client.player == null || client.options == null) return;

        globalTickCounter = (globalTickCounter + 1) % POLL_INTERVAL_TICKS;

        handleKeybinds(client);

        if (macroEnabled) {
            if (isReturning) {
                handleReturnSequence(client);
            } else {
                handleFarmingLogic(client);
            }
        }

        if (autoBreakEnabled && macroEnabled && !isReturning) {
            client.options.keyAttack.setDown(true);
        } else if (client.options.keyAttack.isDown()) {
            client.options.keyAttack.setDown(false);
        }
    }

    private void handleKeybinds(Minecraft client) {
        while (SWITCH_MACRO.consumeClick()) {
            FarmType[] types = FarmType.values();
            currentFarm = types[(currentFarm.ordinal() + 1) % types.length];
            client.player.sendOverlayMessage(Component.literal("Selected Farm: §a" + currentFarm.name()));
        }

        while (TOGGLE_MACRO.consumeClick()) {
            macroEnabled = !macroEnabled;
            client.player.sendOverlayMessage(Component.literal(currentFarm.name() + " Macro: " + (macroEnabled ? "§aON" : "§cOFF")));

            if (macroEnabled) {
                initMacroState(client);
                lockCameraToCurrentFarm(client);
            } else {
                stopAllMovement(client);
                isReturning = false;
                returnPhase = 0;
                waitTicks = 0;
                phaseTicks = 0;

                if (client.player != null && client.getConnection() != null) {
                    client.player.getAbilities().flying = false;
                    client.getConnection().send(new ServerboundPlayerAbilitiesPacket(client.player.getAbilities()));
                }
            }
        }

        while (TOGGLE_AUTOBREAK.consumeClick()) {
            autoBreakEnabled = !autoBreakEnabled;
            client.player.sendOverlayMessage(Component.literal("Auto-Break: " + (autoBreakEnabled ? "§aON" : "§cOFF")));
        }
    }

    private void handleFarmingLogic(Minecraft client) {
        if (globalTickCounter == 0) {
            boolean farmActivityDetected = detectFarmActivity(client);

            if (farmActivityDetected) {
                ticksSinceLastBreak = 0;
            } else {
                ticksSinceLastBreak += POLL_INTERVAL_TICKS;
            }
        }

        if (ticksSinceLastBreak >= MAX_IDLE_TICKS) {
            startReturnSequence(client);
            return;
        }

        int[] activeSequence;
        String[] activeNames;

        switch (currentFarm) {
            case MELON:
                activeSequence = MELON_ZIGZAG;
                activeNames = MELON_NAMES;
                break;
            case COCOA:
                activeSequence = COCOA_SEQUENCE;
                activeNames = COCOA_NAMES;
                break;
            case SUGARCANE:
                activeSequence = SUGARCANE_SEQUENCE;
                activeNames = SUGARCANE_NAMES;
                break;
            case PCBM:
                activeSequence = PCBM_SEQUENCE;
                activeNames = PCBM_NAMES;
                break;
            default:
                // Should be unreachable now that every enum value has its own case,
                // but kept as a safe fallback.
                activeSequence = MELON_ZIGZAG;
                activeNames = MELON_NAMES;
                break;
        }

        zigzagStep = zigzagStep % activeSequence.length;
        int currentDirection = activeSequence[zigzagStep];

        client.options.keyUp.setDown(currentDirection == 0);
        client.options.keyRight.setDown(currentDirection == 1);
        client.options.keyDown.setDown(currentDirection == 2);
        client.options.keyLeft.setDown(currentDirection == 3);
        client.options.keySprint.setDown(currentDirection == 0);

        lockCameraToCurrentFarm(client);

        double dx = client.player.getX() - lastX;
        double dz = client.player.getZ() - lastZ;

        if (Math.sqrt(dx * dx + dz * dz) < MOVE_EPSILON) {
            stuckTicks++;
        } else {
            stuckTicks = 0;
        }

        if (stuckTicks >= STUCK_TICKS_THRESHOLD) {
            zigzagStep = (zigzagStep + 1) % activeSequence.length;
            stuckTicks = 0;
            client.player.setDeltaMovement(0.0, client.player.getDeltaMovement().y, 0.0);
            client.player.sendOverlayMessage(Component.literal("Blocked — now: " + activeNames[zigzagStep]));
        }

        lastX = client.player.getX();
        lastZ = client.player.getZ();
    }

    // Only checks inventory — returns true when total item count increases
    private boolean detectFarmActivity(Minecraft client) {
        int currentItemCount = 0;

        for (int i = 0; i < client.player.getInventory().getContainerSize(); i++) {
            currentItemCount += client.player.getInventory().getItem(i).getCount();
        }

        boolean activity = lastTotalItemCount != -1 && currentItemCount > lastTotalItemCount;
        lastTotalItemCount = currentItemCount;

        return activity;
    }

    private void handleReturnSequence(Minecraft client) {
        if (client.player == null || client.getConnection() == null) return;

        double targetX = startX;
        double targetZ = startZ;

        double dx = targetX - client.player.getX();
        double dz = targetZ - client.player.getZ();
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);

        phaseTicks++;

        // Hard timeout — never let a phase freeze the macro
        if (phaseTicks > PHASE_TIMEOUT) {
            client.player.sendOverlayMessage(Component.literal("§cReturn timed out — forcing resume"));
            forceFinishReturn(client);
            return;
        }

        switch (returnPhase) {

            // ===================== PHASE 1: Initial wait =====================
            case 1:
                stopAllMovement(client);
                waitTicks++;

                if (waitTicks >= INITIAL_WAIT_DURATION) {
                    advancePhase(2, "§eDouble-tapping space to fly...");
                }
                break;

            // ===================== PHASE 2: Double-tap space =====================
            case 2:
                stopAllMovement(client);
                waitTicks++;

                if (waitTicks == 1) {
                    client.options.keyJump.setDown(true);
                } else if (waitTicks == 3) {
                    client.options.keyJump.setDown(false);
                } else if (waitTicks == 5) {
                    client.options.keyJump.setDown(true);
                } else if (waitTicks == 7) {
                    client.options.keyJump.setDown(false);
                } else if (waitTicks >= DOUBLE_TAP_CONFIRM_TICKS) {
                    if (client.player.getAbilities().flying) {
                        returnTargetY = client.player.getY() + 12.0;
                        advancePhase(3, null);
                    } else {
                        waitTicks = 0; // retry
                    }
                }
                break;

            // ===================== PHASE 3: Ascend =====================
            case 3:
                clearHorizontalKeys(client);
                client.options.keyShift.setDown(false);
                client.player.setXRot(-90.0f);

                if (client.player.getY() < returnTargetY) {
                    client.options.keyJump.setDown(true);
                } else {
                    client.options.keyJump.setDown(false);
                    advancePhase(4, null);
                }
                break;

            // ===================== PHASE 4: Short hover =====================
            case 4:
                stopAllMovement(client);
                waitTicks++;

                if (waitTicks >= 8) {
                    advancePhase(5, "§eFlying to start...");
                }
                break;

            // ===================== PHASE 5: Fast travel =====================
            case 5:
                clearHorizontalKeys(client);
                client.options.keyJump.setDown(false);
                client.options.keyShift.setDown(false);

                float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                client.player.setYRot(yaw);
                client.player.setXRot(10.0f);

                if (horizontalDist > 2.0) {
                    client.options.keyUp.setDown(true);
                    client.options.keySprint.setDown(true);
                } else {
                    client.options.keyUp.setDown(false);
                    client.options.keySprint.setDown(false);
                    advancePhase(6, "§eSlowing down...");
                }
                break;

            // ===================== PHASE 6: Let momentum die =====================
            case 6:
                stopAllMovement(client);
                waitTicks++;

                if (waitTicks >= 25) {
                    advancePhase(7, "§ePrecision approach...");
                }
                break;

            // ===================== PHASE 7: Precision approach (flying) =====================
            case 7:
                client.options.keySprint.setDown(false);
                client.options.keyJump.setDown(false);
                client.options.keyShift.setDown(false);
                client.options.keyDown.setDown(false);
                client.options.keyLeft.setDown(false);
                client.options.keyRight.setDown(false);

                float approachYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                client.player.setYRot(approachYaw);
                client.player.setXRot(5.0f);

                waitTicks++;

                if (horizontalDist > 0.12) {
                    client.options.keyUp.setDown(waitTicks % 6 == 0);
                } else {
                    client.options.keyUp.setDown(false);
                    advancePhase(8, "§eOver target — dropping...");
                }
                break;

            // ===================== PHASE 8: Disable fly + drop =====================
            case 8:
                stopAllMovement(client);
                client.player.setXRot(90.0f);

                if (client.player.getAbilities().flying) {
                    client.player.getAbilities().flying = false;
                    client.getConnection().send(new ServerboundPlayerAbilitiesPacket(client.player.getAbilities()));
                }

                waitTicks++;

                if (client.player.onGround() || waitTicks > 60) {
                    advancePhase(9, "§eLanded — final adjust...");
                }
                break;

            // ===================== PHASE 9: Final ground centering =====================
            case 9:
                client.options.keySprint.setDown(false);
                client.options.keyJump.setDown(false);
                client.options.keyShift.setDown(false);
                client.options.keyDown.setDown(false);
                client.options.keyLeft.setDown(false);
                client.options.keyRight.setDown(false);

                waitTicks++;

                if (horizontalDist > 0.10) {
                    float finalYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                    client.player.setYRot(finalYaw);
                    client.options.keyUp.setDown(waitTicks % 7 == 0);
                } else {
                    client.options.keyUp.setDown(false);
                }

                if (waitTicks >= TOUCHDOWN_SYNC_DURATION || horizontalDist <= 0.10) {
                    forceFinishReturn(client);
                }
                break;

            default:
                forceFinishReturn(client);
                break;
        }
    }

    private void advancePhase(int newPhase, String message) {
        returnPhase = newPhase;
        waitTicks = 0;
        phaseTicks = 0;
        if (message != null && Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.sendOverlayMessage(Component.literal(message));
        }
    }

    private void forceFinishReturn(Minecraft client) {
        stopAllMovement(client);
        isReturning = false;
        returnPhase = 0;
        waitTicks = 0;
        phaseTicks = 0;

        zigzagStep = 0;
        stuckTicks = 0;
        ticksSinceLastBreak = 0;
        nearbyBlockCache.clear();
        lastTotalItemCount = -1;

        if (client.player != null) {
            lastX = client.player.getX();
            lastZ = client.player.getZ();
        }

        lockCameraToCurrentFarm(client);
        client.player.sendOverlayMessage(Component.literal("§aReturned — farming resumed"));
    }

    private void startReturnSequence(Minecraft client) {
        isReturning = true;
        returnPhase = 1;
        waitTicks = 0;
        phaseTicks = 0;
        ticksSinceLastBreak = 0;
        stopAllMovement(client);
        client.player.sendOverlayMessage(Component.literal("§eNo inventory change for 5s — returning..."));
    }

    private void initMacroState(Minecraft client) {
        zigzagStep = 0;
        stuckTicks = 0;
        ticksSinceLastBreak = 0;
        isReturning = false;
        returnPhase = 0;
        waitTicks = 0;
        phaseTicks = 0;
        nearbyBlockCache.clear();
        lastTotalItemCount = -1;

        if (client.player != null) {
            startX = Math.floor(client.player.getX()) + 0.5;
            startY = client.player.getY();
            startZ = Math.floor(client.player.getZ()) + 0.5;

            lastX = client.player.getX();
            lastZ = client.player.getZ();
        }
    }

    private void clearHorizontalKeys(Minecraft client) {
        client.options.keyUp.setDown(false);
        client.options.keyDown.setDown(false);
        client.options.keyLeft.setDown(false);
        client.options.keyRight.setDown(false);
        client.options.keySprint.setDown(false);
    }

    private void stopAllMovement(Minecraft client) {
        if (client.options != null) {
            client.options.keyUp.setDown(false);
            client.options.keyDown.setDown(false);
            client.options.keyLeft.setDown(false);
            client.options.keyRight.setDown(false);
            client.options.keySprint.setDown(false);
            client.options.keyJump.setDown(false);
            client.options.keyShift.setDown(false);
        }
    }

    private void lockCameraToCurrentFarm(Minecraft client) {
        if (client.player != null) {
            client.player.setXRot(currentFarm.targetPitch);
            client.player.setYRot(currentFarm.targetYaw);
        }
    }
}