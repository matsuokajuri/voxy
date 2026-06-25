# AGENTS.md

Forge 1.20.1 port of Voxy. One rule governs everything:

> **original Voxy source is the baseline**

Every renderer, model, geometry, visibility, shader, command, lifecycle, and
resource path must be traced to the original Voxy implementation before code is
written.

## 1. Project

- Voxy Forge 1.20.1 renderer migration. Goal: replicate original Voxy renderer
  behavior and performance on Forge as closely as the platform allows.
- Main branch: `forge-1.20.1-skeleton`.
- Environment: Windows, PowerShell, Gradle, Java, Forge 1.20.1.
- Hard client prerequisites: **Embeddium** (Forge replacement for Sodium) and
  **Oculus** (Forge replacement for Iris / the shaderpack frontend). Dev runs
  must load both.

## 2. Supreme rule: port, don't substitute

When original Voxy has a component, data structure, shader contract, lifecycle
owner, algorithm, or resource layout, the Forge path must port or adapt that
EXACT mechanism.

```text
find original Voxy source
 -> inspect dependencies down to the bottom layer
 -> map the exact ownership and data flow
 -> port/adapt to Forge 1.20.1
 -> document only unavoidable Forge-specific deviations
```

**Hard prohibition (applies to every rule below).** No fallback, preview,
synthetic fixture, sample-set bridge, debug renderer, manual-QA command,
offscreen validation, or one-off path may become the formal route or count as
readiness. If such a path still exists in code it is historical/temporary
evidence only — never extend it; remove deprecated paths in controlled batches
once their references are gone.

## 3. The chain to port

The formal route converges on the original Voxy chain:

```text
WorldEngine / WorldSection / Mapper
 -> ModelBakerySubsystem -> ModelFactory
 -> SoftwareModelTextureBakery / TextureUtils / ModelQueries -> ModelStore
 -> RenderGenerationService -> RenderDataFactory -> BuiltSection
 -> BasicAsyncGeometryManager -> BasicSectionGeometryData
 -> RenderDistanceTracker -> HierarchicalOcclusionTraverser
 -> ViewportSelector / Viewport -> MDICViewport
 -> cmdgen.comp -> MDICSectionRenderer -> original terrain shader contract
```

A Forge adapter must preserve the same ownership, data layout, lifecycle, and
performance semantics unless a documented platform blocker makes exact parity
impossible.

## 4. Reading code: CodeGraph first

- Inspect/locate source with CodeGraph: `codegraph_explore` for subsystem
  questions, ownership/data-flow tracing, and multi-symbol context;
  `codegraph_node` for one file or symbol.
- Use direct reads / `rg` only when CodeGraph reports the target is not indexed,
  the target is non-source (docs, config, resources, logs, scripts), or a
  staleness warning needs targeted confirmation. If CodeGraph is unavailable or
  unindexed, say so and fall back.

Before changing a subsystem:

1. Read the original Voxy files that implement it.
2. Read the original dependency classes that feed or consume it.
3. Compare the Forge path against the original path.
4. Remove or deprecate substitute logic instead of building on top of it.
5. Update the parity audit when a deviation is found or fixed.

Key docs:

```text
docs/forge-1.20.1-original-voxy-full-render-path-parity-audit.md   parity audit
docs/forge-1.20.1-deprecated-prototype-routes.md                  deprecated inventory
docs/forge-1.20.1-xi-runtime-visual-investigation-notes-2026-06-23.md  latest investigation trail
```

## 5. Readiness

Do not claim readiness from historical preview or validation evidence. These
stay false until the original-Voxy-equivalent owner exists and is connected:

```text
formalRendererReady=false
actualRendererDrawEnabled=false
formalDrawPipelineReady=false
earlyUsableLodRendererReady=false
```

Preview pixels, offscreen validation, sample atlas uploads, synthetic cmdgen
results, and debug MDIC draws never count toward readiness.

## 6. Round labels

New work uses Roman-numeral rounds; dotted Arabic suffixes are steps inside one
round (e.g. `V`, `V.1`, `V.2_ORIGINAL_CMDGEN_COMP_OUTPUT_PARITY`). Old H/I/J/K/L
labels are historical only — do not reuse. Prefer coherent multi-step rounds over
one tiny commit per step, while still compiling between risky steps and
committing the completed round.

## 7. Validation

Default after a code change:

```powershell
git status
.\gradlew compileJava
```

Run `.\gradlew runClient` only when runtime, GL, visual, or Minecraft resource
behavior must be validated:

- use quick-play when available (default world `新的世界`;
  `-PvoxyQuickPlayWorld=<folder>` for an ASCII world);
- prefer backend/console command input; otherwise minimal in-game chat commands;
- the agent does NOT inspect the screen — ask the user for visual confirmation
  (LOD brightness day/night, water, vanilla/LOD seams, holes);
- do not use ComputerUse unless the user explicitly permits it;
- close Minecraft normally; never kill Java unless explicitly instructed.

## 8. Git & artifacts

Never commit: `run/`, logs, crash reports, saves, local config, build outputs,
`.idea/`, local IDE state, `.agents/`, `.codegraph/`, and `CODEX.md` (user-local
instructions, unless the user explicitly asks). Use focused commit messages;
report commit hash, build status, runClient status (if used), and final git
status. Commit/push only when the user asks.

## 9. Docs

Docs must reflect the current parity route. Rewrite preview/fallback-era docs to
the parity route or mark them superseded. Do not preserve obsolete instructions
as if they are still actionable.

## 10. Working style

Be direct. If Forge code diverges from original Voxy, say so. If parity requires
rewriting thousands of lines, treat that as the correct direction rather than a
shortcut. When in doubt, inspect more original Voxy code before implementing.

## 11. RTK wrappers

Prefer for verbose shell commands: `rtk git status`, `rtk git diff`,
`rtk test <build/test command>`, `rtk log <file>`. Do not read full
`latest.log` / `debug.log` directly.

## 12. Hard-won lessons

- **Shader-stage defines must match across vertex and fragment.** Voxy's terrain
  vertex (`quads3.vert` / `quad_util.glsl`) and fragment (`quads.frag`) share
  `#ifdef PATCHED_SHADER`, and their attribute layout depends on it. Any define
  for the patched/shaderpack path MUST be applied to BOTH stages — exactly as
  original `AbstractSectionRenderer` does by defining on the whole `Shader.make()`
  builder. Defining it on only one stage silently scrambles attributes (light
  byte vs tint) with no GL error. This was the 2026-06-25 black-solid-LOD bug.
- **Diagnose with ground truth, not guesses.** For a runtime visual bug: read ONE
  candidate -> classify it confirmed / excluded / blocked -> record that in the
  investigation doc -> only then read the next. Get ground truth from gated audits
  (e.g. `-Dvoxy.forge.auditLighting`) and from temporary, never-committed GPU/shader
  probes (force a value, visualize an attribute as colour) instead of speculating.
  Revert every probe before committing.
- **The WorldEngine is in-memory** (`voxy-client.toml: enableWorldEngineSkeleton`).
  There is no on-disk LOD cache; each session re-ingests as chunks load, and LOD
  beyond MC render distance reflects only what was ingested this session.
- **Two parallel geometry/MDIC paths exist.** The active render route is
  `ForgeOriginalVoxy*`. A legacy `ForgeVoxy*` / `ForgeCpu*` / `ForgeMdicCommand*`
  path is still wired into `ForgeVoxyInstance` and the Embeddium mixin but does
  NOT drive the visible MDIC render. Do not extend the legacy path; retire it only
  after tracing which path drives the visible render.
