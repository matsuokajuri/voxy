Voxy is an LoD rendering mod for minecraft

## Forge 1.20.1 Voxy parity port

This branch is being remediated toward original Voxy renderer parity on Forge
1.20.1. Original Voxy is the baseline; Forge code must port or adapt the
original model, geometry, visibility, MDIC, shader, and lifecycle mechanisms
rather than replacing them with preview/debug paths.

## Forge frontends

The Forge port requires:

```text
Embeddium
```

Embeddium is the Forge-side replacement for the original Voxy Sodium frontend.
Oculus is optional, matching original Voxy's Iris policy. When installed it is
the Forge-side Iris/shaderpack integration point; without it Voxy uses the
normal render pipeline.

Historical cached-LoD preview commands and debug renderers are deprecated as
implementation direction. See `AGENTS.md`,
`docs/forge-1.20.1-original-voxy-full-render-path-parity-audit.md`, and
`PORTING_NOTES.md` for the current route.
