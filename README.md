Voxy is an LoD rendering mod for minecraft

## Forge 1.20.1 Voxy parity port

This branch is being remediated toward original Voxy renderer parity on Forge
1.20.1. Original Voxy is the baseline; Forge code must port or adapt the
original model, geometry, visibility, MDIC, shader, and lifecycle mechanisms
rather than replacing them with preview/debug paths.

## Required Forge frontends

The Forge port now requires these client-side mods:

```text
Embeddium
Oculus
```

Embeddium is the Forge-side replacement for the original Voxy Sodium frontend.
Oculus is the Forge-side replacement for the original Iris/shaderpack
integration point and provides the Iris mod identity on Forge.

Historical cached-LoD preview commands and debug renderers are deprecated as
implementation direction. See `AGENTS.md`,
`docs/forge-1.20.1-original-voxy-full-render-path-parity-audit.md`, and
`PORTING_NOTES.md` for the current route.
