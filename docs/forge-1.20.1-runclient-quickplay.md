# Forge 1.20.1 runClient quick-play workflow

## Purpose

This is a runtime-validation helper for the current Roman-round original Voxy
parity route. It is not a formal renderer path, not a preview route, and not a
manual QA success criterion by itself.

Use it only when a defect requires real Minecraft, Forge, Embeddium, Oculus,
resource, GL, or visual-output validation.

## Default command

Run client validation through RTK:

```powershell
rtk test .\gradlew runClient
```

The ForgeGradle client run config in `build.gradle` passes:

```text
--quickPlaySingleplayer <world-folder>
```

The default world folder is:

```text
新的世界
```

That value must match a save folder id under:

```text
run/saves/
```

Minecraft 1.20.1 parses `--quickPlaySingleplayer` in
`net.minecraft.client.main.Main`, stores it in
`GameConfig.QuickPlayData.singleplayer`, and
`net.minecraft.client.quickplay.QuickPlay.joinSingleplayerWorld` passes that
string to `levelExists(...)` and `loadLevel(...)`. The argument therefore must
match the save folder id, not just the display name.

To use an ASCII test world in a local checkout, launch with:

```powershell
rtk test .\gradlew runClient -PvoxyQuickPlayWorld=voxy_test_world
```

Do not commit `run/saves`, local launcher files, logs, crash reports, or local
config.

## Runtime Evidence

For log evidence, use targeted RTK log reads:

```powershell
rtk log run/logs/latest.log
```

Do not read full `latest.log` or `debug.log` directly.

Useful evidence to collect when validating the current parity route:

```text
quick-play world actually opened
no repeated Oculus reload/start-cleanup loop
no shaderpack compile fallback when the Voxy patch should be active
no new GL error loop
no lightmap texture-zero status during terrain draw
non-zero MDIC draw-count readback when LoD should be visible
water/fluid model ids do not alias model id 0
```

## Visual Checks

Ask the user only for what logs/status cannot prove:

```text
far LoD terrain brightness under day and night
water/translucent visibility
vanilla chunk to LoD seam
movement/update rhythm while flying
whether the previously fixed polygon-grid artifact stays absent
```

Do not treat screenshots or manual commands as the formal implementation path.
They are runtime evidence for the original-parity path only.

## Failure Diagnosis

If the client lands on the title screen:

```text
check targeted latest.log output for --quickPlaySingleplayer
confirm the value matches a folder under run/saves
if a non-ASCII save folder is suspected, use -PvoxyQuickPlayWorld=<folder>
if Minecraft shows a quick-play invalid identifier screen, the save folder id
was accepted but not found
```

## Manual Fallback

If quick-play fails and runtime validation is still necessary:

1. Capture the title screen or error screen if visual evidence matters.
2. Use targeted `rtk log` output to confirm quick-play arguments or failures.
3. Enter the test world manually.
4. Use only the minimal in-game commands needed for the current validation.
5. Close Minecraft normally.

Do not count the fallback as a quick-play pass.
