# Forge 1.20.1 direct GL heap renderer plan

This document is superseded.

The old direct GL heap renderer idea was a debug proof path. It must not become
the formal renderer route.

## Deprecated prior route

```text
CPU SectionGeometryManager
 -> upload-only GL heap
 -> direct debug renderer
 -> debug draw lists / debug shaders
```

This is historical evidence only.

## Formal replacement direction

The formal route must port original Voxy geometry ownership:

```text
RenderDataFactory
 -> BuiltSection
 -> BasicAsyncGeometryManager
 -> BasicSectionGeometryData
 -> MDICViewport
 -> cmdgen.comp
 -> MDICSectionRenderer
```

## Required parity

Forge must match:

- 128-record alignment behavior;
- section id allocation and reuse;
- geometry pointer allocation/free;
- 32-byte section metadata layout;
- bucket offsets;
- remove/reupload lifecycle;
- renderer consumption through MDIC, not a direct debug renderer.

## Rule

Do not extend the direct GL debug renderer. Replace it with original
`BasicAsyncGeometryManager` / `BasicSectionGeometryData` parity.
