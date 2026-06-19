# AGENTS.md

This repository is a Forge 1.20.1 port of Voxy. The project has now reset its
implementation direction around one rule:

```text
original Voxy source is the baseline
```

Every renderer, model, geometry, visibility, shader, command, lifecycle, and
resource-management path must be traced back to the original Voxy
implementation before code is written.

## 1. Project identity

- Project: Voxy Forge 1.20.1 renderer migration.
- Main branch: `forge-1.20.1-skeleton`.
- Environment: Windows, PowerShell, Gradle, Java, Forge 1.20.1.
- Goal: replicate original Voxy renderer behavior and performance on Forge as
  closely as the platform allows.

## 2. Supreme implementation rule

When original Voxy has a component, data structure, shader contract, lifecycle
owner, algorithm, or resource layout, the Forge path must port or adapt that
exact mechanism.

Required workflow:

```text
find original Voxy source
 -> inspect all dependencies down to the bottom layer
 -> map the exact ownership and data flow
 -> port/adapt to Forge 1.20.1
 -> document only unavoidable Forge-specific deviations
```

Forbidden workflow:

```text
notice original Voxy has custom logic
 -> skip it
 -> substitute a convenient Forge/debug/sample/preview path
 -> call the result formal or ready
```

No fallback, preview, synthetic fixture, sample-set bridge, debug renderer,
manual QA command, or one-off validation path may become the formal route.

## 3. Deprecated history

Older H/I/J/K and K10-preview-era stage notes, proof renderers, sample-set
bridges, synthetic fixtures, and QA-only paths are historical evidence only.
They are deprecated as implementation direction.

Historical code may remain temporarily if command/status references still need
it for compilation, but new work must not extend it. Remove deprecated paths in
controlled batches once their references are no longer needed.

The current route is not "continue K preview work". The current route is:

```text
original Voxy parity remediation
```

## 4. Original Voxy chain to port

The formal Forge route must converge on the original Voxy chain:

```text
WorldEngine / WorldSection / Mapper
 -> ModelBakerySubsystem
 -> ModelFactory
 -> SoftwareModelTextureBakery / TextureUtils / ModelQueries
 -> ModelStore
 -> RenderGenerationService
 -> RenderDataFactory
 -> BuiltSection
 -> BasicAsyncGeometryManager
 -> BasicSectionGeometryData
 -> RenderDistanceTracker
 -> HierarchicalOcclusionTraverser
 -> ViewportSelector / Viewport
 -> MDICViewport
 -> cmdgen.comp
 -> MDICSectionRenderer
 -> original terrain shader contract
```

If a Forge-specific adapter is necessary, it must preserve the same ownership,
data layout, lifecycle, and performance semantics unless a documented platform
blocker makes exact parity impossible.

## 5. Parity audit requirements

Before changing a subsystem:

1. Read the original Voxy files that implement that subsystem.
2. Read the original dependency classes that feed or consume it.
3. Compare the current Forge path against the original path.
4. Remove or deprecate substitute logic instead of building on top of it.
5. Update the parity audit when a deviation is found or fixed.

Important audit document:

```text
docs/forge-1.20.1-original-voxy-full-render-path-parity-audit.md
```

Deprecated prototype inventory:

```text
docs/forge-1.20.1-deprecated-prototype-routes.md
```

## 6. Formal readiness rules

Do not claim readiness from historical preview or validation evidence.

These must stay false until the original Voxy-equivalent owner exists and is
connected:

```text
formalRendererReady=false
actualRendererDrawEnabled=false
formalDrawPipelineReady=false
earlyUsableLodRendererReady=false
```

Preview-visible pixels, offscreen validation, sample atlas uploads, synthetic
cmdgen results, and debug MDIC draws do not count as formal renderer readiness.

## 7. Validation cadence

Use fewer game loops and larger coherent implementation batches, but do not
skip verification.

Default validation:

```powershell
git status
.\gradlew compileJava
```

Run `.\gradlew runClient` only when runtime behavior, Minecraft resource state,
GL behavior, or visual output must be validated. If runClient is needed:

- use quick-play when available;
- prefer backend/console/RCON command input if available;
- if no backend command input exists, report that and use minimal in-game chat
  commands;
- do not use ComputerUse unless the user explicitly permits it;
- close Minecraft normally;
- never kill Java unless explicitly instructed.

## 8. Git and artifact rules

Never commit:

```text
run/
logs
crash reports
saves
local config
build outputs
.idea/
.vscode local machine state
```

`CODEX.md` may exist locally as user-provided instructions. Do not commit it
unless the user explicitly asks.

Use focused commit messages. Report commit hash, build status, runClient status
if used, and final git status.

## 9. Documentation rules

Docs must reflect the current parity route. If an old document contains
preview-era or fallback-era instructions, either rewrite it to the parity route
or mark it superseded/deprecated.

Do not preserve obsolete instructions as if they are still actionable.

## 10. Working style

Be direct. If current code diverges from original Voxy, say so. If parity
requires rewriting thousands of lines, treat that as the correct direction
rather than reaching for a shortcut.

When in doubt, inspect more original Voxy code before implementing.
