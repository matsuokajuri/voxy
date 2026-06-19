# K0 original Voxy renderer alignment audit

This document is superseded.

The old K0 verdict allowed a K1 no-draw owner and later K-stage preview work.
That conclusion is no longer current. The project has adopted strict bottom-up
original Voxy parity as the controlling rule.

## Current verdict

```text
K0_VERDICT_SUPERSEDED_BY_STRICT_ORIGINAL_VOXY_PARITY
```

## Why superseded

K0 correctly identified many original Voxy owners, but it still permitted a
staged Forge preview route to continue. That route later accumulated preview,
synthetic, sample-set, and fallback logic. Those paths are now deprecated.

## Current rule

Do not use K0 to justify:

```text
K-stage preview owners
J-stage shader previews
sample-set bridges
synthetic fixtures
temporary model-id rewrites
debug MDIC command buffers
no-draw skeletons as route progress
```

Current work must instead inspect and port the original Voxy chain:

```text
VoxyRenderSystem
 -> ModelBakerySubsystem / ModelFactory / ModelStore
 -> RenderGenerationService / RenderDataFactory
 -> BasicAsyncGeometryManager / BasicSectionGeometryData
 -> RenderDistanceTracker / HierarchicalOcclusionTraverser
 -> MDICViewport / cmdgen.comp / MDICSectionRenderer
 -> terrain shader contract
```

## Replacement documents

Use these as current guidance:

```text
AGENTS.md
docs/forge-1.20.1-stage-roadmap.md
docs/forge-1.20.1-original-voxy-full-render-path-parity-audit.md
docs/forge-1.20.1-deprecated-prototype-routes.md
```
