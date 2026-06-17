# Forge 1.20.1 runClient quick-play workflow

## Purpose

QA1 is a test-harness reliability task. It does not advance H-stage renderer
functionality and does not change renderer code.

The goal is for:

```powershell
.\gradlew runClient
```

to enter the local singleplayer test world directly so validation starts from a
known in-world state.

## Current configuration

The ForgeGradle client run config in `build.gradle` passes:

```text
--quickPlaySingleplayer <world-folder>
```

The default world folder is:

```text
新的世界
```

This is the actual folder currently present under:

```text
run/saves/新的世界
```

Minecraft 1.20.1 parses `--quickPlaySingleplayer` in
`net.minecraft.client.main.Main`, stores it in
`GameConfig.QuickPlayData.singleplayer`, and
`net.minecraft.client.quickplay.QuickPlay.joinSingleplayerWorld` passes that
string to `levelExists(...)` and `loadLevel(...)`. That means the argument must
match the save folder id, not just the display name.

To use an ASCII test world in a local checkout, launch with:

```powershell
.\gradlew runClient -PvoxyQuickPlayWorld=voxy_test_world
```

Do not commit `run/saves`, local launcher files, logs, or crash reports.

## Success criteria

After `runClient` starts:

- the first observed Minecraft screen should be an in-world view, not the title
  screen or world list;
- `run/logs/latest.log` should show `--quickPlaySingleplayer` in the launch
  arguments;
- the integrated server should start without manually selecting a world;
- the tester should still execute `/tp @s 128 140 128` and confirm the chat/log
  teleport feedback before continuing.

## Failure diagnosis

If the client lands on the title screen:

- check `run/logs/latest.log` for `--quickPlaySingleplayer`;
- confirm the value matches a folder under `run/saves`;
- if the value is non-ASCII and the argument is present but quick-play still
  fails, create or copy a local ASCII-named test world and run with
  `-PvoxyQuickPlayWorld=<folder>`;
- if Minecraft shows a quick-play invalid identifier screen, the argument was
  accepted but the save folder could not be found.

## Manual fallback

If quick-play fails during validation:

1. screenshot the title screen or error screen;
2. read `latest.log` for quick-play arguments or failure evidence;
3. use keyboard navigation to enter the test world;
4. execute `/tp @s 128 140 128`;
5. screenshot and confirm the chat/log feedback;
6. close Minecraft normally.

Do not treat the fallback as a quick-play pass.
