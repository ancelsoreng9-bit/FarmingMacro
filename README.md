# MelonMacro

A client-side Fabric mod that automates repetitive farming movement in Minecraft. It walks a zigzag pattern across your farm, optionally holds attack to break crops, and automatically flies back to its starting position if farming stalls.

> **Disclaimer:** Automation macros are against the rules of many servers and can get your account banned. This is a client-side utility; use it only where automation is allowed (singleplayer, your own server, or servers that explicitly permit it). You use it at your own risk.

## Features

- **Four farm modes** with their own camera angles and movement patterns
- **Zigzag movement** that advances to the next step when you get blocked
- **Auto-Break** toggle that holds the attack key while the macro runs
- **Idle detection**: if your inventory item count doesn't increase for 5 seconds, the macro assumes the farm is stuck and runs a return sequence
- **Automatic return-to-start**: flies up, travels back to the start position, lands, and resumes farming
- **Overlay messages** for every state change (farm selected, macro on/off, blocked, returning, etc.)

## Controls

Keybinds are registered under the **Miscellaneous** category and can be rebound in *Options → Controls → Key Binds*.

| Default key | Action |
|-------------|--------|
| `I` | Cycle to the next farm type |
| `P` | Toggle the macro on/off |
| `O` | Toggle Auto-Break (hold attack) |

## Farm Modes

Each mode locks your camera to a fixed pitch and yaw while the macro is running.

| Farm | Pitch | Yaw | Movement pattern |
|------|-------|-----|------------------|
| `MELON` | -58.5 | -90 | Forward → Right → Forward → Left |
| `COCOA` | -58.0 | -180 | Forward → Right → Forward → Left *(placeholder)* |
| `SUGARCANE` | 0.0 | -50 | Left → Forward → Left → Backward |
| `PCBM` | 0.0 | -90 | Forward → Right → Forward → Left *(placeholder)* |

> **Note:** There is no in-game setting for pitch and yaw. If you want to change them, you have to edit them directly in the code (the `FarmType` enum in `MelonMacroClient.java`) and rebuild the mod.

`COCOA` and `PCBM` currently share the same placeholder pattern as `MELON`. Edit their sequences in the source to match your farm layout (see [Customizing](#customizing)).

## How It Works

### Farming loop

1. Press `P` to enable the macro. Your current position (centered on the block) is saved as the **start position**.
2. Every tick, the mod presses the movement key for the current step of the active pattern and re-locks the camera. Sprint is held only on Forward steps.
3. If your horizontal movement is below a small threshold for 15 ticks (about 0.75 s), you're considered **blocked** and the macro moves on to the next step in the pattern.
4. Every 5 ticks, your inventory item count is compared with the previous check. An increase counts as farming activity and resets the idle timer.

### Return sequence

If no activity is detected for 100 ticks (5 seconds), the macro returns to the start position:

| Phase | What happens |
|-------|--------------|
| 1 | Stop and wait 2 seconds |
| 2 | Double-tap jump to enable creative flight (retries until flying) |
| 3 | Ascend 12 blocks |
| 4 | Short hover |
| 5 | Fast sprint-fly toward the start position |
| 6 | Let momentum die |
| 7 | Slow precision approach over the start block |
| 8 | Disable flight and drop straight down |
| 9 | Fine ground centering, then resume farming |

Each phase has a hard 10-second timeout, so a stuck phase can never freeze the macro; it forces a resume instead.

> The return sequence relies on creative-style flight (`abilities.flying`), so it only works where your character is allowed to fly.

## Requirements

- Minecraft (a version using the `KeyMapping.Category` API, e.g. recent 1.21.x releases)
- [Fabric Loader](https://fabricmc.net/use/)
- [Fabric API](https://modrinth.com/mod/fabric-api)
- Java 21+

## Building

```bash
./gradlew build
```

The compiled jar will be in `build/libs/`. Drop it into your `mods` folder alongside Fabric API.

## Customizing

All tunables live at the top of `MelonMacroClient.java`.

**Movement patterns.** Directions are encoded as `0 = Forward`, `1 = Right`, `2 = Backward`, `3 = Left`:

```java
private static final int[] SUGARCANE_SEQUENCE = { 3, 0, 3, 2 };
private static final String[] SUGARCANE_NAMES = { "Left", "Forward", "Left", "Backward" };
```

Change the `int[]` and matching `String[]` for `COCOA_SEQUENCE` / `PCBM_SEQUENCE` to suit your farm.

**Camera angles.** Pitch and yaw can only be changed directly in the code. Edit the values in the `FarmType` enum, then rebuild:

```java
public enum FarmType {
    MELON(-58.5F, -90.0F),     // (pitch, yaw)
    COCOA(-58.0F, -180.0F),
    SUGARCANE(0.0F, -50.0F),
    PCBM(0.0F, -90.0F);
    ...
}
```

**Timing and thresholds:**

| Constant | Default | Purpose |
|----------|---------|---------|
| `MOVE_EPSILON` | `0.01` | Movement below this per tick counts as not moving |
| `STUCK_TICKS_THRESHOLD` | `15` | Ticks of not moving before advancing a step |
| `MAX_IDLE_TICKS` | `100` | Ticks without inventory gain before returning (5 s) |
| `POLL_INTERVAL_TICKS` | `5` | How often inventory is checked |
| `INITIAL_WAIT_DURATION` | `40` | Wait before starting the return flight |
| `PHASE_TIMEOUT` | `200` | Max ticks any single return phase may take |

**Adding a new farm type.** Add an entry to `FarmType` with its pitch/yaw, define a sequence and names array, and add a matching `case` in the `switch` inside `handleFarmingLogic`.

## Project Structure

```
melonmacro/client/
└── MelonMacroClient.java   # Entire mod: keybinds, farming logic, return sequence
```

## Troubleshooting

- **Wrong movement pattern for a farm:** check that the farm has its own `case` in the `switch` in `handleFarmingLogic`; anything without one falls through to `default`.
- **Macro keeps returning to start:** the idle check only counts *increases* in total inventory items. If your inventory is full or the crop drops don't stack in, it will look idle.
- **Return flight never starts flying:** flight must be allowed for your player (creative, or a server/mod that grants it).
- **Macro won't stop:** press `P` again; disabling clears all held keys and turns flight off.
