# Forxy deferred enhancement TODOs

These items are optional Forxy product enhancements, not original-author debt and
not original-Voxy parity requirements. They must not block the 壹轮—拾壹轮 debt
plan.

## TODO: animated Vanilla/LOD boundary transition

Status: deferred; do not implement in the current round.

Original Voxy deliberately disables Sodium's region fade while its renderer is
active (`MixinRenderRegionManager.voxy$cancelFade`) and removes Iris' chunk-fade
marker while loading shader patches (`IrisShaderPatch.makePatch`). Therefore this
must remain a separately gated Forxy enhancement, with parity behavior as the
default.

- [ ] Add an `LOD boundary transition animation` option, default **off**.
- [ ] Keep one prepared Vanilla overlap ring for the main terrain pass; do not
      increase the already reserved three-ring Oculus shadow-caster load range.
- [ ] Start a transition only after the corresponding Vanilla mesh is ready. If
      it is not ready, keep the LOD contribution fully visible so no hole can
      appear.
- [ ] Use a world-anchored blue-noise/dither dissolve over approximately 0.4
      seconds. Do not use ordinary alpha blending or geometry morphing, which
      would introduce ghosting, depth ambiguity, and z-fighting.
- [ ] Make entering and leaving Vanilla range use reversible versions of the same
      transition contract.
- [ ] Apply the transition only to the main terrain render. Never fade or discard
      the hidden Vanilla geometry used by the Oculus shadow pass; the cross-
      boundary shadow repair must remain fully active and stable.
- [ ] Implement and qualify opaque and cutout geometry first, including leaves.
      Keep translucent terrain such as water disabled until it has a separate,
      ordering-safe design.
- [ ] Add source-contract tests for default-off behavior, mesh-readiness gating,
      reversible timing, main-pass-only masking, and Oculus shadow-pass bypass.
- [ ] Visually qualify slow and fast movement, camera rotation, render-distance
      changes, chunk rebuilds, dimension changes, shaderpack toggles, leaves, and
      Vanilla/LOD boundary shadows with both Embeddium-only and Embeddium +
      Oculus clients.
- [ ] Measure frame time and GPU cost before enabling the option in any shipped
      configuration.

Acceptance gate: no holes, double terrain, z-fighting, temporal crawling, shadow
regression, or translucent ordering regression; disabling the option reproduces
the current original-parity abrupt switch exactly.
