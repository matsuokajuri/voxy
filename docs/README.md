# Forge 1.20.1 Voxy docs index

Status of each doc relative to the current original-Voxy parity route. Historical
docs are kept as evidence (see `AGENTS.md` §9); they are not deleted, only marked.

## Active references

Consult these for the current route:

- [original-voxy-full-render-path-parity-audit](forge-1.20.1-original-voxy-full-render-path-parity-audit.md)
  — the parity audit; the canonical Forge-vs-original comparison.
- [formal-renderer-readiness-audit](forge-1.20.1-formal-renderer-readiness-audit.md)
  — readiness flags and the required original owners.
- [deprecated-prototype-routes](forge-1.20.1-deprecated-prototype-routes.md)
  — inventory of deprecated preview/debug/sample routes (do not extend).
- [embeddium-oculus-frontend-mapping](forge-1.20.1-embeddium-oculus-frontend-mapping.md)
  — Sodium→Embeddium / Iris→Oculus frontend mapping.
- [original-voxy-unported-content-migration-reference](forge-1.20.1-original-voxy-unported-content-migration-reference-2026-06-23.md)
  — original source-area inventory and final ported/adapted/platform-N/A classification.
- [runclient-quickplay](forge-1.20.1-runclient-quickplay.md)
  — runClient quick-play workflow (default world `新的世界`).
- [Forxy deferred enhancement TODOs](forxy-deferred-enhancement-todos.md)
  — optional post-parity product improvements; not original-author debt.
- [Forxy 捌轮 model/material preparation](forxy-round8-model-material-preparation.md)
  — packed-data inventory, capacity decision, risk map, and execution order.

## Resolved

- [xi-runtime-visual-investigation-notes-2026-06-23](forge-1.20.1-xi-runtime-visual-investigation-notes-2026-06-23.md)
  — the black-solid-LOD investigation trail; **root cause found and fixed**
  (commit 24c5503c): `PATCHED_SHADER` was defined only on the fragment, not the
  terrain vertex shader. Kept for the diagnostic method.
- [black-lod-bugfix-plan-2026-06-22](forge-1.20.1-black-lod-bugfix-plan-2026-06-22.md)
  — RESOLVED; superseded by the investigation notes above.
- [xi-confirmed-defect-repair-plan-2026-06-23](forge-1.20.1-xi-confirmed-defect-repair-plan-2026-06-23.md)
  — RESOLVED; superseded by the investigation notes above.

## Historical audits & investigation trails

Dated evidence from earlier passes; not current instructions:

- [deep-runtime-drift-audit-2026-06-21](forge-1.20.1-deep-runtime-drift-audit-2026-06-21.md)
- [full-code-defect-audit-2026-06-23](forge-1.20.1-full-code-defect-audit-2026-06-23.md)
- [k0-original-voxy-renderer-alignment-audit](forge-1.20.1-k0-original-voxy-renderer-alignment-audit.md)
- [debug-preview-cleanup-2026-06-22](forge-1.20.1-debug-preview-cleanup-2026-06-22.md)
- [stage-roadmap](forge-1.20.1-stage-roadmap.md)

## Historical plans

Early bridge/integration/retirement plans; the work they scoped is largely done or
deprecated. Keep for context, do not treat as current instructions:

- [modelstore-bridge-plan](forge-1.20.1-modelstore-bridge-plan.md)
- [model-bakery-bridge-plan](forge-1.20.1-model-bakery-bridge-plan.md)
- [mdic-command-buffer-plan](forge-1.20.1-mdic-command-buffer-plan.md)
- [direct-gl-heap-renderer-plan](forge-1.20.1-direct-gl-heap-renderer-plan.md)
- [formal-mdic-renderer-integration-plan](forge-1.20.1-formal-mdic-renderer-integration-plan.md)
- [legacy-code-retirement-plan](forge-1.20.1-legacy-code-retirement-plan.md)
