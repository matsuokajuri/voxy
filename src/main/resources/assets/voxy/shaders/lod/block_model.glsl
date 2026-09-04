struct BlockModel {
    uint faceData[6];
    uint flagsA;
    uint colourTint;
    uint customId;
    uint _pad[7];
};

// Face-relative distance from the bakery view plane in 1/64 units. The producer emits
// 0..62; code 63 retains the original end-plane encoding. setupQuad applies the face
// sign exactly once, so an additional per-face 1/64 correction would move valid faces.
float extractFaceIndentation(uint faceData) {
    uint enc = (faceData>>16)&63u;
    enc += uint(enc==63u);//convert 63 to 64 cause of pain reasons
    return float(enc)/64.0;
}

vec4 extractFaceSizes(uint faceData) {
    return (vec4(faceData&0xFu, (faceData>>4)&0xFu, (faceData>>8)&0xFu, (faceData>>12)&0xFu)/16.0)+vec4(0.0,1.0/16.0,0.0,1.0/16.0);
}

uint faceHasAlphaCuttout(uint faceData) {
    return (faceData>>22)&1u;
}

// Partial model bounds need alpha testing when merged across more than one block tile.
uint faceHasAlphaCuttoutOverride(uint faceData) {
    return (faceData>>23)&1u;
}

uint faceTintState(uint faceData) {
    return (faceData>>24)&3u;
}

bool modelHasBiomeLUT(BlockModel model) {
    return ((model.flagsA)&2u) != 0;
}

bool modelIsTranslucent(BlockModel model) {
    return ((model.flagsA)&4u) != 0;
}

bool modelIsShaded(BlockModel model) {
    return ((model.flagsA)&8u) != 0;
}
