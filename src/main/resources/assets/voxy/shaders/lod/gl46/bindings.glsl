layout(binding = 0, std140) uniform SceneUniform {
    mat4 MVP;
    ivec3 baseSectionPos;
    uint frameId;
    vec3 cameraSubPos;
};

// GL DrawElementsIndirectCommand ABI: five scalars, std430 stride 20 bytes.
// A wider command is a separate measured design change, not padding for safety.
struct DrawCommand {
    uint  count;
    uint  instanceCount;
    uint  firstIndex;
    int  baseVertex;
    uint  baseInstance;
};


#ifdef BLOCK_MODEL_TEXTURE_BINDING
layout(binding = BLOCK_MODEL_TEXTURE_BINDING) uniform sampler2D blockModelAtlas;
#endif


#ifndef Quad
#define Quad ivec2
#endif
#ifdef QUAD_BUFFER_BINDING
layout(binding = QUAD_BUFFER_BINDING, std430) readonly restrict buffer QuadBuffer {
    Quad quadData[];
};
#endif

#ifdef DRAW_BUFFER_BINDING
layout(binding = DRAW_BUFFER_BINDING, std430) writeonly restrict buffer DrawBuffer {
    DrawCommand cmdBuffer[];
};
#endif

#ifdef DRAW_COUNT_BUFFER_BINDING
layout(binding = DRAW_COUNT_BUFFER_BINDING, std430) restrict buffer DrawCommandCountBuffer {
    uint cmdGenDispatchX;
    uint cmdGenDispatchY;
    uint cmdGenDispatchZ;

    uint opaqueDrawCount;
    uint translucentDrawCount;
    uint temporalOpaqueDrawCount;

    DrawCommand cullDrawIndirectCommand;

    // The original dispatch/count/cull ABI occupies bytes 0..43 unchanged.
    // These cumulative diagnostics use the previously unused tail of the 1 KiB
    // buffer. Prep resets admitted work, but not evidence of rejected work.
    uint commandOverflowFlags;
    uint rejectedOpaqueCommands;
    uint rejectedTranslucentCommands;
    uint rejectedTemporalCommands;
    uint rejectedCommandInputs;
    uint rejectedTranslucentBuilds;
};

const uint COMMAND_OVERFLOW_OPAQUE = 1u;
const uint COMMAND_OVERFLOW_TRANSLUCENT = 2u;
const uint COMMAND_OVERFLOW_TEMPORAL = 4u;
const uint COMMAND_INVALID_INPUT = 8u;
const uint COMMAND_INVALID_TRANSLUCENT_BUILD = 16u;
const uint INVALID_COMMAND_INDEX = 0xffffffffu;
#endif

#ifdef SECTION_METADATA_BUFFER_BINDING
layout(binding = SECTION_METADATA_BUFFER_BINDING, std430) readonly restrict buffer SectionBuffer {
    SectionMeta sectionData[];
};
#endif

#ifdef INDIRECT_SECTION_LOOKUP_BINDING
layout(binding = INDIRECT_SECTION_LOOKUP_BINDING, std430) readonly restrict buffer IndirectSectionLookupBuffer {
    uint sectionCount;
    uint indirectLookup[];
};
#endif

#ifndef VISIBILITY_ACCESS
#define VISIBILITY_ACCESS readonly
#endif
#ifdef VISIBILITY_BUFFER_BINDING
layout(binding = VISIBILITY_BUFFER_BINDING, std430) VISIBILITY_ACCESS restrict buffer VisibilityBuffer {
    uint visibilityData[];
};
#endif

#ifdef MODEL_BUFFER_BINDING
layout(binding = MODEL_BUFFER_BINDING, std430) readonly restrict buffer ModelBuffer {
    BlockModel modelData[];
};
#endif

#ifdef MODEL_COLOUR_BUFFER_BINDING
layout(binding = MODEL_COLOUR_BUFFER_BINDING, std430) readonly restrict buffer ModelColourBuffer {
    uint colourData[];
};
#endif

#ifdef POSITION_SCRATCH_BINDING
#ifndef POSITION_SCRATCH_ACCESS
#define POSITION_SCRATCH_ACCESS readonly
#endif
layout(binding = POSITION_SCRATCH_BINDING, std430) POSITION_SCRATCH_ACCESS restrict buffer PositionScratchBuffer {
    uvec2 positionBuffer[];
};
#endif

