# Forge 1.20.1 legacy code retirement plan

This plan tracks cleanup of old proof, debug, preview, and fallback code after
the project reset to original Voxy parity.

## Rule

Do not extend legacy routes. A route may stay temporarily only when deleting it
would break compilation before its command/status/instance references are
removed.

## Current high-pain areas

### ForgeVoxyCommands

Status: deprecated legacy monolithic command surface.

Problem:

```text
formal commands
debug commands
sample-set commands
preview commands
old K-stage QA commands
clear/status helpers
```

are all registered in one file.

Retirement sequence:

```text
1. historical: keep /voxy parity_route_status as the visible boundary - done in
   the proof phase; XXV later deleted the status-only command and DTO chain
2. add no new commands to ForgeVoxyCommands - active rule
3. split legacy debug/GPU/MDIC command registration into ForgeVoxyLegacyDebugCommands - done
4. split formal renderer status commands into ForgeVoxyFormalRendererCommands - done
5. split first preview/sample command group into ForgeVoxyLegacyPreviewCommands - done
6. split K-stage preview/update commands into ForgeVoxyLegacyKPreviewCommands - done
7. split preset subtree into ForgeVoxyPresetCommands - done
8. split geometry/ingest command registration into ForgeVoxyGeometryPipelineCommands - done
9. split model pipeline command registration into ForgeVoxyModelPipelineCommands - done
10. split remaining K1-K4 owner command registration into ForgeVoxyFormalOwnerCommands - done
11. move K1-K4 owner command handler bodies into ForgeVoxyFormalOwnerCommands - done
12. delete handlers once backing prototype objects are removed from ForgeVoxyInstance
13. delete ForgeVoxyCommands when no longer needed
```

### Debug/proof renderers

Status: deprecated in code.

Affected first batch:

```text
ForgeDirectGpuGeometryRenderer
ForgeSimpleGpuMeshRenderer
ForgeDebugMeshRenderer
ForgeGpuGeometryReadbackDebugRenderer
ForgeMdicDebugRenderer
ForgeTexturedDebugQuadRenderer
ForgeTexturedReadbackRenderer
ForgeTexturedMdicDebugRenderer
```

Retirement sequence:

```text
1. remove command enable/draw entry points
2. remove ForgeVoxyInstance ownership fields and getters
3. remove broad debug clear references
4. delete renderer classes and their shader/buffer helper classes
```

### Preview and sample-set routes

Status: deprecated in code and documentation.

These remain temporarily only as historical evidence and compile dependencies.
They must not provide formal renderer readiness.

## Do not delete yet

Do not delete these classes in isolation while `ForgeVoxyCommands` and
`ForgeVoxyInstance` still reference them. Delete by owner group so `compileJava`
stays useful after each cleanup batch.

## Next cleanup batch

The command registration split is complete, and the K1-K4 formal owner handler
bodies have left the monolithic command file. The next cleanup should start
retiring backing prototype owner fields by owner group.

`ForgeVoxyLegacyDebugCommands` already owns the first migrated legacy debug
registration group. `ForgeVoxyLegacyPreviewCommands` owns the first migrated
preview/sample group: J3-J5 preview commands and textured debug/readback
commands. `ForgeVoxyLegacyKPreviewCommands` owns K5-K54 preview/update
commands, `ForgeVoxyFormalRendererCommands` owns formal renderer status
commands, `ForgeVoxyPresetCommands` owns the preset subtree,
`ForgeVoxyGeometryPipelineCommands` owns ingest/geometry commands,
`ForgeVoxyModelPipelineCommands` owns model pipeline commands, and
`ForgeVoxyFormalOwnerCommands` owns the K1-K4 formal owner skeleton command
set.

## 2026-07-03 batch executed: legacy geometry/MDIC island removed (XIX)

The "affected first batch" renderer classes listed above were already deleted by
fe4691dc. The remaining legacy geometry/MDIC path was mapped and found to be a
CLOSED ISLAND referenced only by ForgeVoxyInstance (fields/getters/register/
clear) plus intra-family references — no commands, no mixins, no active-path
classes. Removed in one batch (38 classes):

```text
ForgeCpu* (except ForgeCpuMeshLayer), ForgeGpuGeometry*, ForgeMdicCommand*,
ForgeMdicVisibility*, ForgeSectionGeometry*, ForgeVoxyBuiltSection*,
ForgeVoxyGeometryBuffer, ForgeVoxyGeometryCache, ForgeVoxyGreedyMesher,
ForgeVoxyQuadEncoder
```

XXII follow-up: the active `ForgeCpuMeshLayer` was renamed to
`ForgeOriginalVoxyModelLayer` without changing its model-bakery behavior.
`ForgeVoxyRuntimeOverrides` and the unused mdicCommand*/CPU/BuiltSection/GPU
prototype config entries were removed in the same controlled schema sweep. The
remaining config now maps original Voxy behavior plus the bounded Forge chunk
rediscovery adapter.

compileJava passed immediately after the deletion, confirming the island
mapping.

## 2026-07-16 re-audit: retirement remains complete (XXIX.1)

CodeGraph plus exact source-reference scans reconfirmed that the historical
`ForgeCpu*`, `ForgeGpuGeometry*`, `ForgeMdicCommand*`,
`ForgeMdicVisibility*`, `ForgeSectionGeometry*`, and
`ForgeVoxyBuiltSection*` families remain absent. `ForgeVoxyInstance` owns only
the current `ForgeOriginalVoxyModelPipeline`, and both the Embeddium cutout hook
and the Acedium hook call its single `renderOriginalVoxyAfterTerrain` entry.

The package-local `RenderGenerationService`, `RenderDataFactory`,
`BuiltSection`, `AsyncNodeManager`, `BasicSectionGeometryData`, and
`MDICSectionRenderer` must not be classified as the removed island. They are the
active Forge adaptations constructed by `ForgeOriginalVoxyRenderSystem` and
carry the visible original-parity geometry/MDIC route. XXIX.1 adds a source
contract test that locks both sides of this boundary: retired family names stay
absent, while the active construction chain remains connected.
