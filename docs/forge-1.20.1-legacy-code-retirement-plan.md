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
1. keep /voxy parity_route_status as the visible boundary - done
2. add no new commands to ForgeVoxyCommands - active rule
3. split legacy debug/GPU/MDIC command registration into ForgeVoxyLegacyDebugCommands - done
4. split formal renderer status commands into ForgeVoxyFormalRendererCommands - done
5. split first preview/sample command group into ForgeVoxyLegacyPreviewCommands - done
6. split K-stage preview/update commands into ForgeVoxyLegacyKPreviewCommands - done
7. split preset subtree into ForgeVoxyPresetCommands - done
8. move original-Voxy-parity commands into focused registrar classes
9. delete handlers once backing prototype objects are removed from ForgeVoxyInstance
10. delete ForgeVoxyCommands when no longer needed
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

The next cleanup should split the command registration surface:

```text
ForgeVoxyParityCommands
ForgeVoxyModelPipelineCommands
ForgeVoxyGeometryPipelineCommands
```

`ForgeVoxyLegacyDebugCommands` already owns the first migrated legacy debug
registration group. `ForgeVoxyLegacyPreviewCommands` owns the first migrated
preview/sample group: J3-J5 preview commands and textured debug/readback
commands. `ForgeVoxyLegacyKPreviewCommands` owns K5-K54 preview/update
commands, `ForgeVoxyFormalRendererCommands` owns formal renderer status
commands, and `ForgeVoxyPresetCommands` owns the preset subtree. The remaining
split should move the actual original-Voxy-parity model and geometry command
surfaces out of the old monolithic file before owner deletion begins.
