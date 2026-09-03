# XXXIX — Forxy compatibility merge and quick audit (2026-09-03)

Status: integration and default build/test/package gates passed; the merged
Forxy artifact still requires user-operated in-game regression. This record is
a Forge compatibility integration, not completion of Forxy's planned 拾轮 or
拾壹轮 technical-debt work.

## Merge provenance and scope

- Destination: local `Forxy`, previously
  `e99887ee6f3ea4012cbe73d775a6bfdb6769dab2` (`Refactor Forge renderer pipeline`).
  That pre-existing local commit was already one commit ahead of `origin/Forxy`
  and is preserved, not replaced by the older remote tip.
- Source: `forge-1.20.1-skeleton`, common ancestor `648aa0540957c957ce1f53ecc189f0a6d858e7dd`.
- Source round XXXVII: `0c991b7d804161977b55a3d8b33c17217d5e4099`,
  `XXXVII: make GL debug annotations coexist with ImmediatelyFast`.
- Source round XXXVIII: `3c377c8e1001b24b0ee79953dd5f5c1db80671bb`,
  `XXXVIII: ingest completed vanilla Starlight and Chunky light data`.
- Integration uses a real, non-fast-forward merge. The two repair commits and
  Forxy's existing history remain separately identifiable. No remote push was
  requested or performed.

The source fixes were reviewed and tested before integration. The affected
pre-repair ingest, retry, instance, Chunky, GlDebug, and Mixin-plugin files were
identical at the source base and Forxy destination. Their patches applied
without code conflicts. The four new source/test files had no destination-name
collisions.

Only the main parity audit had a content conflict: both branches appended
independent records after XXXVI. Resolution retains Forxy's visual-root-cause
and 玖轮 preparation/completion records, followed by the complete XXXVII/XXXVIII
investigation history. The obsolete constructor-backfill attempt is explicitly
marked historical. The Mixin JSON merged automatically and retains Forxy's
two Oculus shadow-caster-range registrations as well as the two new lighting
adapters.

## Imported behavior and original-Voxy baseline

| Area | Imported change | Boundary preserved |
| --- | --- | --- |
| ImmediatelyFast / GL diagnostics | Replace exclusive Voxy `@Redirect` with priority-1100, cancellable, optional `@Inject` before the logging invocation. | Original `MixinGlDebug` annotates Voxy-originated messages and suppresses capability probes. Foreign messages continue through vanilla/ImmediatelyFast; Minecraft's debug-ring update already occurred before cancellation. No new MixinExtras dependency. |
| Vanilla chunk/light packet | Capture coordinates only after `PacketUtils.ensureRunningOnSameThread` and wrap the exact queued light Runnable; execute the original body before ingestion. | Captured level/x/z belong to that packet. No network-thread coordinate overwrite and no global chunk scan. |
| Starlight | Select a priority-900 RETURN adapter after Starlight's priority-1001 light/chunk handler. | Vanilla and Starlight adapters are mutually exclusive. Starlight's discarded vanilla Runnable is not mistaken for a completed callback. |
| Chunky | Consume the exact successful server FULL-future result through the common trusted-light entry. | No later coordinate relookup or ticket-removal race; original current-client integrated-server/dimension/identity checks remain. |
| Common ingest / retry | Accept implicit light storage only at the trusted completion boundary, retain the narrow ambient-full-bright exception, and cancel a completed whole-chunk retry in O(1). | Same `VoxelIngestService`, conversion, Mapper, WorldUpdater, saving, and dirty-notification owners. An unrelated single-section success still does not erase a chunk retry. |

The original Sodium chunk-added/removed/update hooks remain the baseline;
Forge 1.20.1 needs the extra packet-completion boundary because chunk publication
can precede application of the associated lighting. This is an adapter to that
boundary, not a replacement ingest subsystem.

This is **not** a modpack-name switch. Without Starlight, the vanilla completion
adapter is active; with Starlight, its replacement is active. Chunky hooks are
selected only when Chunky is present. Ordinary early captures still use the
strict readiness gate, except for the documented `ambientLight >= 1.0` and
null/empty-light special case. A completed client packet or Chunky FULL result
uses the common trusted entry in either lighting environment.

No modpack paths, shaderpack names, configuration overrides, dependency-version
changes, extra renderer, new storage schema, or disk-format migration were
introduced. Forxy's existing storage recovery, service accounting, mipping,
model/material, RenderDataFactory, and Oculus shadow changes were not rewritten.

## Quick-audit results

No merge-blocking defect was found in this bounded integration audit. Checks:

- Reviewed the imported production/test diff and original GlDebug/Sodium ingest
  contracts; retained one production ingest owner and existing world/session
  identity checks.
- Checked client-thread coordinate confinement, delayed Runnable local capture,
  original-light-before-ingest ordering, mutually exclusive optional-mod
  selection, full-chunk versus section retry cancellation, and exact Chunky
  future ownership.
- Checked compatibility with Forxy's existing `Service` lifecycle/accounting
  API; the repair does not change its queue, permit, or shutdown implementation.
- Confirmed no incoming diff to Forxy's build configuration, shader resources,
  renderer/model/mesh owners, storage layout, or readiness control code.
- Confirmed both source rounds and Forxy-only documentary history survive;
  conflict markers and `git diff --check` errors are absent.
- Checked the reobfuscated artifact for new lighting/GlDebug classes, preserved
  Forxy `RenderFaceDecision` and shadow-caster-range classes, Mixin registrations,
  and production refmap targets.

Inherited housekeeping observation, not introduced or changed here: Forxy
already tracks 512 paths under `bin/`. They are not part of this merge diff and
the generated formal JAR contains no `bin/` entries. Cleaning that historical
tracked output is separate work, not an excuse to delete files during this
integration. `capsettings.cap` remains untouched and untracked.

This is not a new line-by-line audit of the entire Forxy renderer and does not
close the remaining GPU/hierarchy roadmap. No performance, external-backend,
exact-artifact, or real-data tagged gate is claimed from the default `test` run.

## Validation on the combined Forxy tree

From `D:\Projects\voxy`:

```powershell
rtk proxy .\gradlew clean test jarJar `
  "-PvoxyEmbeddiumDevJar=.gradle/local-inputs/embeddium-0.3.31+mc1.20.1.jar" `
  "-PvoxyOculusDevJar=Oculus-1.20.1-new/build/libs/oculus-mc1.20.1-1.8.0.jar" `
  --rerun-tasks --console=plain
```

- `compileJava`, `compileTestJava`, default `test`, `jarJar`, and `reobfJarJar`:
  **passed**; 14 tasks executed, build successful in approximately one minute.
- Default tests: **70 suites / 253 tests / 0 failures / 0 errors / 0 skips**.
  These are the combined Forxy results, not the earlier source-only 140 tests.
- Existing deprecation/removal and unchecked-use compiler warnings remain;
  no compilation or Mixin annotation-processing errors occurred.
- Artifact: `build/libs/voxy-forge-0.2.17-beta-forge-all.jar`, **12,684,556 bytes**.
- SHA-256: `af709a9db34aedd06b5e02d45f1d27a9493df27af4d9eac9270e0c296a243cb6`.
- Archive checks: **0 duplicate entries**, **0 unintended workspace entries**
  (`bin/`, `run/`, `.codegraph/`, or `capsettings.cap`).
- Packaged refmap retains `handleLevelChunkWithLight -> m_183388_`,
  `queueLightUpdate -> m_194171_`,
  `ensureRunningOnSameThread -> m_131363_`, and `printDebugLog -> m_84038_`.

## Runtime evidence and remaining gate

Prior source-branch modpack testing confirmed world entry and visible LOD with
and without Starlight. That is useful evidence for the imported repair, but is
not a substitute for testing the newly combined Forxy artifact.

The reported Closing Song save/quit freeze recovered automatically. Its source-
artifact log records Xaero finalization from 14:46:40.231 to 14:49:29.318
(169.087 seconds), then normal world saving and Voxy cleanup from 14:49:30.136
to 14:49:30.353 (217 ms). Live samples found Xaero map-color work calling Ecliptic
Seasons and waiting in C2ME. No Xaero/Ecliptic/C2ME workaround is included here;
the observations do not prove or disprove every indirect timing interaction.

No client was launched during this merge/quick-audit task. Before treating the
combined artifact as runtime-qualified, the user must check:

- Embeddium-only, without Oculus or Starlight: initial load, LOD light/water and
  vanilla/LOD boundary, dimension changes, and normal exit.
- Embeddium + Oculus without Starlight: shader on/off and shaderpack switching,
  F3+T resource reload, reconnect/restart, and normal exit.
- Starlight + Chunky: generate a genuinely new area in the active dimension,
  verify distant LOD without first visiting it as vanilla chunks, then verify
  dimension/exit/restart behavior. Previously generated chunks skipped by
  Chunky do not exercise the new FULL-result callback.

No readiness flag or planned Forxy round is promoted by this static gate. No
packaged JAR has been installed into either user's modpack by this task.
