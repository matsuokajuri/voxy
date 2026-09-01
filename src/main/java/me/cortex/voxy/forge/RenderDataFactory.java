package me.cortex.voxy.forge;

import me.cortex.voxy.common.Logger;
import me.cortex.voxy.common.util.MemoryBuffer;
import me.cortex.voxy.common.util.UnsafeUtil;
import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.common.world.WorldSection;
import me.cortex.voxy.common.world.other.Mapper;
import org.lwjgl.system.MemoryUtil;

import java.util.Arrays;


final class RenderDataFactory {
    private static final boolean BUILD_OCCUPANCY_SET = false;

    private static final boolean CHECK_NEIGHBOR_FACE_OCCLUSION = true;
    private static final boolean VERIFY_MESHING = Boolean.getBoolean("voxy.verifyMeshing");

    // Round VI measurements rejected cloning ordinary generated geometry into a directional
    // cache; dirty-neighbour invalidation remains owned by the bounded late-result cache.

    //Ok so the idea for fluid rendering is to make it use a seperate mesher and use a different code path for it
    // since fluid states are explicitly overlays over the base block
    // can do funny stuff like double rendering


    private final WorldEngine world;
    private final ModelFactory modelMan;

    //private final long[] sectionData = new long[32*32*32*2];
    private final long[] sectionData = new long[32*32*32*2];
    private final long[] neighboringFaces = new long[32*32*6];
    //private final int[] neighboringOpaqueMasks = new int[32*6];

    private final int[] opaqueMasks = new int[32*32];
    private final int[] nonOpaqueMasks = new int[32*32];
    private final int[] fluidMasks = new int[32*32];//Used to separately mesh fluids, allowing for fluid + blockstate


    // These arrays are reusable worker-local scan scratch. Final quads are emitted directly
    // into the native bucket buffer below, so this path does not allocate per-section arrays.

    private static final int QUAD_BUCKET_COUNT = 8;
    private static final int QUADS_PER_BUCKET = 1 << 16;
    private static final int MAX_QUADS_PER_BUCKET = QUADS_PER_BUCKET - 1;

    //Storage has 65,536 slots; the 16-bit command count can represent at most 65,535 quads.
    private final MemoryBuffer quadBuffer = new MemoryBuffer(
            (long) Long.BYTES * QUAD_BUCKET_COUNT * QUADS_PER_BUCKET);//6 faces + dual direction + translucents
    private final long quadBufferPtr = this.quadBuffer.address;
    private final int[] quadCounters = new int[QUAD_BUCKET_COUNT];


    private int minX;
    private int minY;
    private int minZ;
    private int maxX;
    private int maxY;
    private int maxZ;

    private int quadCount = 0;

    private final OccupancySet occupancy;

    //Wont work for double sided quads
    private final class Mesher extends ScanMesher2D {
        public int auxiliaryPosition = 0;
        public boolean doAuxiliaryFaceOffset = true;
        public int axis = 0;//Y,Z,X

        //Note x, z are in top right
        @Override
        protected void emitQuad(int x, int z, int length, int width, long data) {
            if (VERIFY_MESHING) {
                if (length<1||length>16) {
                    throw new IllegalStateException("length out of bounds: " + length);
                }
                if (width<1||width>16) {
                    throw new IllegalStateException("width out of bounds: " + width);
                }
                if (x<0||x>31) {
                    throw new IllegalStateException("x out of bounds: " + x);
                }
                if (z<0||z>31) {
                    throw new IllegalStateException("z out of bounds: " + z);
                }
                if (x-(length-1)<0 || z-(width-1)<0) {
                    throw new IllegalStateException("dim out of bounds: " + (x-(length-1))+", " + (z-(width-1)));
                }
            }

            x -= length-1;
            z -= width-1;

            if (this.axis == 2) {
                //Need to swizzle the data if on x axis
                int tmp = x;
                x = z;
                z = tmp;

                tmp = length;
                length = width;
                width = tmp;
            }

            //Lower 26 bits can be auxiliary data since that is where quad position information goes;
            int auxData = (int) (data&((1<<26)-1));
            data &= ~((1L<<26)-1);

            int axisSide = auxData&1;
            int type = (auxData>>1)&3;//Translucent, double side, directional

            if (VERIFY_MESHING) {
                if (type == 3) {
                    throw new IllegalStateException();
                }
            }

            //Shift up if is negative axis
            int auxPos = this.auxiliaryPosition;
            auxPos += 1-(this.doAuxiliaryFaceOffset?axisSide:1);//Shift

            if (VERIFY_MESHING) {
                if (auxPos > 31) {
                    throw new IllegalStateException("OOB face: " + auxPos + ", " + axisSide);
                }
            }

            final int axis = this.axis;
            int face = (axis<<1)|axisSide;

            int encodedPosition = face;
            encodedPosition |= ((width - 1) << 7) | ((length - 1) << 3);
            encodedPosition |= x << (axis==2?16:21);
            encodedPosition |= z << (axis==1?16:11);
            int shiftAmount = axis==0?16:(axis==1?11:21);
            //shiftAmount += ;
            encodedPosition |= auxPos << (shiftAmount);

            long quad = data | Integer.toUnsignedLong(encodedPosition);


            int bufferIdx = type+(type==2?face:0);//Translucent, double side, directional
            int bucketQuadCount = RenderDataFactory.this.quadCounters[bufferIdx];
            if (bucketQuadCount >= MAX_QUADS_PER_BUCKET) {
                throw new IllegalStateException(
                        "render-quad-bucket-capacity-exceeded: bucket=" + bufferIdx
                                + ", max=" + MAX_QUADS_PER_BUCKET);
            }
            RenderDataFactory.this.quadCounters[bufferIdx] = bucketQuadCount + 1;
            RenderDataFactory.this.quadCount++;
            long bufferOffset = (long) bucketQuadCount * Long.BYTES
                    + (long) bufferIdx * Long.BYTES * QUADS_PER_BUCKET;
            MemoryUtil.memPutLong(RenderDataFactory.this.quadBufferPtr + bufferOffset, quad);


            //Update AABB bounds
            if (axis == 0) {//Y
                RenderDataFactory.this.minY = Math.min(RenderDataFactory.this.minY, auxPos);
                RenderDataFactory.this.maxY = Math.max(RenderDataFactory.this.maxY, auxPos);

                RenderDataFactory.this.minX = Math.min(RenderDataFactory.this.minX, x);
                RenderDataFactory.this.maxX = Math.max(RenderDataFactory.this.maxX, x + length);

                RenderDataFactory.this.minZ = Math.min(RenderDataFactory.this.minZ, z);
                RenderDataFactory.this.maxZ = Math.max(RenderDataFactory.this.maxZ, z + width);
            } else if (axis == 1) {//Z
                RenderDataFactory.this.minZ = Math.min(RenderDataFactory.this.minZ, auxPos);
                RenderDataFactory.this.maxZ = Math.max(RenderDataFactory.this.maxZ, auxPos);

                RenderDataFactory.this.minX = Math.min(RenderDataFactory.this.minX, x);
                RenderDataFactory.this.maxX = Math.max(RenderDataFactory.this.maxX, x + length);

                RenderDataFactory.this.minY = Math.min(RenderDataFactory.this.minY, z);
                RenderDataFactory.this.maxY = Math.max(RenderDataFactory.this.maxY, z + width);
            } else {//X
                RenderDataFactory.this.minX = Math.min(RenderDataFactory.this.minX, auxPos);
                RenderDataFactory.this.maxX = Math.max(RenderDataFactory.this.maxX, auxPos);

                RenderDataFactory.this.minY = Math.min(RenderDataFactory.this.minY, x);
                RenderDataFactory.this.maxY = Math.max(RenderDataFactory.this.maxY, x + length);

                RenderDataFactory.this.minZ = Math.min(RenderDataFactory.this.minZ, z);
                RenderDataFactory.this.maxZ = Math.max(RenderDataFactory.this.maxZ, z + width);
            }
        }
    }

    private final Mesher blockMesher = new Mesher();
    private final Mesher seondaryblockMesher = new Mesher();//Used for dual non-opaque geometry

    public RenderDataFactory(WorldEngine world, ModelFactory modelManager, boolean emitMeshlets) {
        this(world, modelManager, emitMeshlets, false);
    }
    public RenderDataFactory(WorldEngine world, ModelFactory modelManager, boolean emitMeshlets, boolean generateOccupancy) {
        this.world = world;
        this.modelMan = modelManager;
        if (generateOccupancy && BUILD_OCCUPANCY_SET) {
            this.occupancy = new OccupancySet();
        } else {
            this.occupancy = null;
        }
    }

    static long getQuadTyping(long metadata) {//2 bits
        return 0b111L&(0b000_000_010_100L>>(ModelQueries._isTranslucent(metadata)*6+ModelQueries._isDoubleSided(metadata)*3));
    }

    private static long packPartialQuadData(int modelId, long state, long metadata) {
        //This uses hardcoded data to shuffle things
        long lightAndBiome =  (state&((0x1FFL<<47)|(0xFFL<<56)))>>>1;
        lightAndBiome &= ~(ModelQueries._notIsBiomeColoured(metadata) * (0x1FFL << 46));//46 not 47 because is already shifted by 1 THIS WASTED 4 HOURS ;-; aaaaaAAAAAA
        lightAndBiome &= ~(ModelQueries._isFullyOpaque(metadata)*(0xFFL << 55));//If its fully opaque it always uses neighbor light?

        long quadData = lightAndBiome;
        quadData |= Integer.toUnsignedLong(modelId)<<26;
        quadData |= getQuadTyping(metadata);//Returns the typing already shifted by 1
        return quadData;
    }

    private int prepareSectionData(final long[] rawSectionData) {
        final var sectionData = this.sectionData;
        final var rawModelIds = this.modelMan._unsafeRawAccess();
        long opaque = 0;
        long notEmpty = 0;
        long pureFluid = 0;
        long partialFluid = 0;

        int neighborAcquireMskAndFlags = 0;//-+x, -+z, -+y
        int i = 0;
        for (int q = 0; q < 512; q++) {
            for (int j = 0; j < 64; i++, j++) {
                long block = rawSectionData[i];//Get the block mapping
                if (Mapper.isAir(block)) {//If it is air, just emit lighting
                    sectionData[i * 2] = (block & (0xFFL << 56)) >>> 1;
                    sectionData[i * 2 + 1] = 0;
                } else {
                    int modelId = rawModelIds[Mapper.getBlockId(block)];
                    if (modelId == -1) {//Failed, so just return error
                        return Mapper.getBlockId(block) | (1 << 31);
                    }
                    if (modelId == 0) {//modelId == 0, its basicly air so set it as air
                        sectionData[i * 2] = (block & (0xFFL << 56)) >>> 1;
                        sectionData[i * 2 + 1] = 0;
                    } else {
                        // ModelFactory already owns the model-id keyed metadata cache. Keeping
                        // this scratch keyed by model id avoids a second invalidation owner.
                        long modelMetadata = this.modelMan.getModelMetadataFromClientId(modelId);

                        sectionData[i * 2] = packPartialQuadData(modelId, block, modelMetadata);
                        sectionData[i * 2 + 1] = modelMetadata;

                        notEmpty |= 1L << j;
                        opaque |= ModelQueries._isFullyOpaque(modelMetadata)<<j;
                        pureFluid |= ModelQueries._isFluid(modelMetadata)<<j;
                        partialFluid |= ModelQueries._containsFluid(modelMetadata)<<j;
                    }
                }
            }
            if (notEmpty != 0) {
                long nonOpaque = (notEmpty^opaque)&~pureFluid;
                long fluid = pureFluid|partialFluid;
                this.opaqueMasks[(i >> 5) - 2] = (int) opaque;
                this.opaqueMasks[(i >> 5) - 1] = (int) (opaque>>>32);
                this.nonOpaqueMasks[(i >> 5) - 2] = (int) nonOpaque;
                this.nonOpaqueMasks[(i >> 5) - 1] = (int) (nonOpaque>>>32);
                this.fluidMasks[(i >> 5) - 2] = (int) fluid;
                this.fluidMasks[(i >> 5) - 1] = (int) (fluid>>>32);

                neighborAcquireMskAndFlags |= getNeighborMsk(notEmpty, i);
                neighborAcquireMskAndFlags |= opaque!=0?(1<<6):0;

                opaque = 0;
                notEmpty = 0;
                pureFluid = 0;
                partialFluid = 0;
            }
        }
        return neighborAcquireMskAndFlags;
    }

    private static int getNeighborMsk(long notEmpty, int i) {
        int packedEmpty = (int) ((notEmpty >>>32)| notEmpty);

        int neighborMsk = 0;
        //-+x
        neighborMsk += packedEmpty&1;//-x
        neighborMsk += (packedEmpty>>>30)&0b10;//+x

        //notEmpty = (notEmpty != 0)?1:0;
        neighborMsk += ((((i - 1) >> 10) == 0) ? 0b100 : 0)*(packedEmpty!=0?1:0);//-y
        neighborMsk += ((((i - 1) >> 10) == 31) ? 0b1000 : 0)*(packedEmpty!=0?1:0);//+y
        neighborMsk += (((((i - 33) >> 5) & 0x1F) == 0) ? 0b10000 : 0)*(((int) notEmpty)!=0?1:0);//-z
        neighborMsk += (((((i - 1) >> 5) & 0x1F) == 31) ? 0b100000 : 0)*((notEmpty >>>32)!=0?1:0);//+z
        return neighborMsk;
    }

    private void acquireNeighborData(WorldSection section, int msk) {
        // Snapshot each requested face while its neighbouring section is acquired. On-demand
        // raw access after release would violate section lifetime and concurrent-reuse safety.
        if ((msk&1)!=0) {//-x
            var sec = this.world.acquire(section.lvl, section.x - 1, section.y, section.z);
            //Note this is not thread safe! (but eh, fk it)
            var raw = sec._unsafeGetRawDataArray();
            for (int i = 0; i < 32*32; i++) {
                this.neighboringFaces[i] = raw[(i<<5)+31];//pull the +x faces from the section
            }
            sec.release(WorldSection.RELEASE_HINT_POSSIBLE_REUSE);
        }
        if ((msk&2)!=0) {//+x
            var sec = this.world.acquire(section.lvl, section.x + 1, section.y, section.z);
            //Note this is not thread safe! (but eh, fk it)
            var raw = sec._unsafeGetRawDataArray();
            for (int i = 0; i < 32*32; i++) {
                this.neighboringFaces[i+32*32] = raw[(i<<5)];//pull the -x faces from the section
            }
            sec.release(WorldSection.RELEASE_HINT_POSSIBLE_REUSE);
        }

        if ((msk&4)!=0) {//-y
            var sec = this.world.acquire(section.lvl, section.x, section.y - 1, section.z);
            //Note this is not thread safe! (but eh, fk it)
            var raw = sec._unsafeGetRawDataArray();
            for (int i = 0; i < 32*32; i++) {
                this.neighboringFaces[i+32*32*2] = raw[i|(0x1F<<10)];//pull the +y faces from the section
            }
            sec.release(WorldSection.RELEASE_HINT_POSSIBLE_REUSE);
        }
        if ((msk&8)!=0) {//+y
            var sec = this.world.acquire(section.lvl, section.x, section.y + 1, section.z);
            //Note this is not thread safe! (but eh, fk it)
            var raw = sec._unsafeGetRawDataArray();
            for (int i = 0; i < 32*32; i++) {
                this.neighboringFaces[i+32*32*3] = raw[i];//pull the -y faces from the section
            }
            sec.release(WorldSection.RELEASE_HINT_POSSIBLE_REUSE);
        }

        if ((msk&16)!=0) {//-z
            var sec = this.world.acquire(section.lvl, section.x, section.y, section.z - 1);
            //Note this is not thread safe! (but eh, fk it)
            var raw = sec._unsafeGetRawDataArray();
            for (int i = 0; i < 32*32; i++) {
                this.neighboringFaces[i+32*32*4] = raw[expandBits(i,0b11111_00000_11111)|(0x1F<<5)];//pull the +z faces from the section
            }
            sec.release(WorldSection.RELEASE_HINT_POSSIBLE_REUSE);
        }
        if ((msk&32)!=0) {//+z
            var sec = this.world.acquire(section.lvl, section.x, section.y, section.z + 1);
            //Note this is not thread safe! (but eh, fk it)
            var raw = sec._unsafeGetRawDataArray();
            for (int i = 0; i < 32*32; i++) {
                this.neighboringFaces[i+32*32*5] = raw[expandBits(i,0b11111_00000_11111)];//pull the -z faces from the section
            }
            sec.release(WorldSection.RELEASE_HINT_POSSIBLE_REUSE);
        }
    }

    private static final long LM = RenderFaceDecision.LIGHT_MASK;

    private RenderFaceDecision.Result decideBlockFace(
            int face,
            long quad,
            long meta,
            long neighborQuad,
            long neighborMeta
    ) {
        return RenderFaceDecision.evaluate(
                face,
                quad,
                meta,
                neighborQuad,
                neighborMeta,
                this.modelMan);
    }

    private int resolveExternalModelId(long mappingId) {
        int blockId = Mapper.getBlockId(mappingId);
        return blockId == 0 ? 0 : this.modelMan.getModelId(blockId);
    }

    private static long packExternalNeighborQuad(int modelId, long mappingId) {
        return ((long) modelId << 26) | ((mappingId & (0xFFL << 56)) >>> 1);
    }

    private int resolveFluidModelId(long quad, long metadata) {
        int modelId = RenderFaceDecision.modelId(quad);
        return ModelQueries.containsFluid(metadata)
                ? this.modelMan.getFluidClientStateId(modelId)
                : modelId;
    }

    private static long retargetQuad(long quad, int modelId, long metadata) {
        return (quad & ~(RenderFaceDecision.MODEL_ID_MASK | 0b110L))
                | ((long) modelId << 26)
                | getQuadTyping(metadata);
    }

    private RenderFaceDecision.Result decideFluidFace(
            int face,
            long fluidQuad,
            long fluidMetadata,
            long neighborQuad,
            long neighborMetadata
    ) {
        RenderFaceDecision.Result baseDecision = decideBlockFace(
                face,
                fluidQuad,
                fluidMetadata,
                neighborQuad,
                neighborMetadata);
        if (!baseDecision.meshes() || !ModelQueries.containsFluid(neighborMetadata)) {
            return baseDecision;
        }

        int neighborFluidModelId = this.modelMan.getFluidClientStateId(
                RenderFaceDecision.modelId(neighborQuad));
        long neighborFluidMetadata = this.modelMan.getModelMetadataFromClientId(neighborFluidModelId);
        long neighborFluidQuad = retargetQuad(
                neighborQuad,
                neighborFluidModelId,
                neighborFluidMetadata);
        return decideBlockFace(
                face,
                fluidQuad,
                fluidMetadata,
                neighborFluidQuad,
                neighborFluidMetadata);
    }

    private void meshFluidFace(
            int face,
            long quad,
            long metadata,
            long neighborQuad,
            long neighborMetadata,
            Mesher mesher
    ) {
        int fluidModelId = resolveFluidModelId(quad, metadata);
        long fluidMetadata = fluidModelId == RenderFaceDecision.modelId(quad)
                ? metadata
                : this.modelMan.getModelMetadataFromClientId(fluidModelId);
        long fluidQuad = retargetQuad(quad, fluidModelId, fluidMetadata);
        RenderFaceDecision.Result decision = decideFluidFace(
                face,
                fluidQuad,
                fluidMetadata,
                neighborQuad,
                neighborMetadata);
        if (decision.meshes()) {
            mesher.putNext(applyQuadLight(
                    (long) (face & 1)
                            | (fluidQuad & ~LM)
                            | RenderFaceDecision.selectedLight(decision, fluidQuad, neighborQuad),
                    fluidMetadata));
        } else {
            mesher.skip(1);
        }
    }

    private int differingFluidMaskYZ(int pidx, int skipAmount, int candidates) {
        int different = 0;
        while (candidates != 0) {
            int index = Integer.numberOfTrailingZeros(candidates);
            candidates &= ~Integer.lowestOneBit(candidates);
            int first = index + pidx * 32;
            int second = index + (pidx + skipAmount) * 32;
            if (resolveFluidModelId(this.sectionData[first * 2], this.sectionData[first * 2 + 1])
                    != resolveFluidModelId(this.sectionData[second * 2], this.sectionData[second * 2 + 1])) {
                different |= 1 << index;
            }
        }
        return different;
    }

    private int differingFluidMaskX(int y, int z, int candidates) {
        int different = 0;
        while (candidates != 0) {
            int index = Integer.numberOfTrailingZeros(candidates);
            candidates &= ~Integer.lowestOneBit(candidates);
            int first = index + z * 32 + y * 32 * 32;
            int second = first + 1;
            if (resolveFluidModelId(this.sectionData[first * 2], this.sectionData[first * 2 + 1])
                    != resolveFluidModelId(this.sectionData[second * 2], this.sectionData[second * 2 + 1])) {
                different |= 1 << index;
            }
        }
        return different;
    }

    private void meshNonOpaqueFace(int face, long quad, long meta, long neighborQuad, long neighborMeta, Mesher mesher) {
        RenderFaceDecision.Result decision = decideBlockFace(
                face,
                quad,
                meta,
                neighborQuad,
                neighborMeta);
        if (decision.meshes()) {
            mesher.putNext(applyQuadLight(
                    (long) (face&1) |
                    (quad&~LM) |
                    RenderFaceDecision.selectedLight(decision, quad, neighborQuad),
                    meta));
        } else {
            mesher.skip(1);
        }
    }

    private static long applyQuadLight(long quad, long selfmeta) {
        final long BLMSK = 0xFL<<(55+4);//block light mask
        long bl = quad&BLMSK;
        bl = Math.max(bl, ModelQueries.lightEmission(selfmeta)<<(55+4));
        quad &= ~(BLMSK);
        quad |= bl;
        return quad;
    }

    private void generateYZOpaqueInnerGeometry(int axis) {
        for (int layer = 0; layer < 31; layer++) {
            this.blockMesher.auxiliaryPosition = layer;
            int cSkip = 0;
            for (int other = 0; other < 32; other++) {
                int pidx = axis==0 ?(layer*32+other):(other*32+layer);
                int skipAmount = axis==0?32:1;

                int current = this.opaqueMasks[pidx];
                int next = this.opaqueMasks[pidx + skipAmount];

                int msk = current ^ next;
                if (msk == 0) {
                    cSkip += 32;
                    continue;
                }

                this.blockMesher.skip(cSkip);
                cSkip = 0;

                int faceForwardMsk = msk & current;
                int cIdx = -1;
                while (msk != 0) {
                    int index = Integer.numberOfTrailingZeros(msk);//Is also the x-axis index
                    int delta = index - cIdx - 1;
                    cIdx = index; //index--;
                    if (delta != 0) this.blockMesher.skip(delta);
                    msk &= ~Integer.lowestOneBit(msk);

                    int facingForward = ((faceForwardMsk >> index) & 1);

                    {
                        int idx = index + (pidx*32);
                        int shift = skipAmount * 32 * 2;

                        //Flip data with respect to facing direction
                        int iA = idx * 2 + (facingForward == 1 ? 0 : shift);
                        int iB = idx * 2 + (facingForward == 1 ? shift : 0);

                        long selfModel = this.sectionData[iA];
                        long selfMeta  = this.sectionData[iA+1];
                        long nextModel = this.sectionData[iB];

                        int face = (axis << 1) | facingForward;
                        long neighborMeta = this.sectionData[iB + 1];
                        RenderFaceDecision.Result decision = decideBlockFace(
                                face,
                                selfModel,
                                selfMeta,
                                nextModel,
                                neighborMeta);
                        if (!decision.meshes()) {
                            this.blockMesher.skip(1);
                            continue;
                        }

                        this.blockMesher.putNext(
                            applyQuadLight(
                                ((long) facingForward) |//Facing
                                (selfModel&~LM) |
                                RenderFaceDecision.selectedLight(decision, selfModel, nextModel),
                                selfMeta
                            ));
                    }
                }

                this.blockMesher.endRow();
            }
            this.blockMesher.finish();
        }
    }

    private void generateYZOpaqueOuterGeometry(int axis) {
        this.blockMesher.doAuxiliaryFaceOffset = false;
        //Hacky generate section side faces (without check neighbor section)
        for (int side = 0; side < 2; side++) {//-, +
            int layer = side == 0 ? 0 : 31;
            this.blockMesher.auxiliaryPosition = layer;
            int cSkips = 0;
            for (int other = 0; other < 32; other++) {
                int pidx = axis == 0 ? (layer * 32 + other) : (other * 32 + layer);
                int msk = this.opaqueMasks[pidx];
                if (msk == 0) {
                    cSkips += 32;
                    continue;
                }

                this.blockMesher.skip(cSkips);
                cSkips = 0;

                int cIdx = -1;
                while (msk != 0) {
                    int index = Integer.numberOfTrailingZeros(msk);//Is also the x-axis index
                    int delta = index - cIdx - 1;
                    cIdx = index; //index--;
                    if (delta != 0) this.blockMesher.skip(delta);
                    msk &= ~Integer.lowestOneBit(msk);

                    {
                        int idx = index + (pidx * 32);


                        int neighborIdx = ((axis+1)*32*32 * 2)+(side)*32*32;
                        long neighborId = this.neighboringFaces[neighborIdx + (other*32) + index];
                        long A = this.sectionData[idx * 2];
                        long selfMeta = this.sectionData[idx * 2 +1];

                        int neighborModelId = resolveExternalModelId(neighborId);
                        long neighborMeta = neighborModelId == 0
                                ? 0L
                                : this.modelMan.getModelMetadataFromClientId(neighborModelId);
                        long neighborQuad = packExternalNeighborQuad(neighborModelId, neighborId);
                        RenderFaceDecision.Result decision = decideBlockFace(
                                (axis << 1) | side,
                                A,
                                selfMeta,
                                neighborQuad,
                                neighborMeta);
                        if (!decision.meshes()) {
                            this.blockMesher.skip(1);
                            continue;
                        }



                        this.blockMesher.putNext(applyQuadLight(
                                ((side == 0) ? 0L : 1L) |
                                (A&~LM) |
                                RenderFaceDecision.selectedLight(decision, A, neighborQuad),
                                selfMeta
                                )
                        );
                    }
                }
                this.blockMesher.endRow();
            }

            this.blockMesher.finish();
        }
        this.blockMesher.doAuxiliaryFaceOffset = true;
    }

    private void generateYZFluidInnerGeometry(int axis) {
        this.seondaryblockMesher.axis = axis;
        this.seondaryblockMesher.doAuxiliaryFaceOffset = true;
        for (int layer = 0; layer < 31; layer++) {
            this.blockMesher.auxiliaryPosition = layer;
            this.seondaryblockMesher.auxiliaryPosition = layer;
            int cSkip = 0;
            for (int other = 0; other < 32; other++) {
                int pidx = axis==0 ?(layer*32+other):(other*32+layer);
                int skipAmount = axis==0?32:1;

                int current = this.fluidMasks[pidx];
                int next = this.fluidMasks[pidx + skipAmount];

                int msk = (current | this.opaqueMasks[pidx]) ^ (next | this.opaqueMasks[pidx + skipAmount]);
                msk &= current|next;
                int differingFluids = differingFluidMaskYZ(pidx, skipAmount, current & next);
                msk |= differingFluids;
                if (msk == 0) {
                    cSkip += 32;
                    continue;
                }

                this.blockMesher.skip(cSkip);
                this.seondaryblockMesher.skip(cSkip);
                cSkip = 0;

                int faceForwardMsk = msk & current;
                int cIdx = -1;
                while (msk != 0) {
                    int index = Integer.numberOfTrailingZeros(msk);//Is also the x-axis index
                    int delta = index - cIdx - 1;
                    cIdx = index; //index--;
                    if (delta != 0) {
                        this.blockMesher.skip(delta);
                        this.seondaryblockMesher.skip(delta);
                    }
                    msk &= ~Integer.lowestOneBit(msk);

                    int facingForward = ((faceForwardMsk >> index) & 1);

                    {
                        int idx = index + (pidx*32);

                        int a = idx*2;
                        int b = (idx + skipAmount * 32) * 2;

                        if ((differingFluids & (1 << index)) != 0) {
                            meshFluidFace(
                                    (axis << 1) | 1,
                                    this.sectionData[a],
                                    this.sectionData[a + 1],
                                    this.sectionData[b],
                                    this.sectionData[b + 1],
                                    this.blockMesher);
                            meshFluidFace(
                                    axis << 1,
                                    this.sectionData[b],
                                    this.sectionData[b + 1],
                                    this.sectionData[a],
                                    this.sectionData[a + 1],
                                    this.seondaryblockMesher);
                            continue;
                        }

                        //Flip data with respect to facing direction
                        int ai = facingForward == 1 ? a : b;
                        int bi = facingForward == 1 ? b : a;

                        long A = this.sectionData[ai];
                        long Am = this.sectionData[ai+1];
                        meshFluidFace(
                                (axis << 1) | facingForward,
                                A,
                                Am,
                                this.sectionData[bi],
                                this.sectionData[bi + 1],
                                this.blockMesher);
                        this.seondaryblockMesher.skip(1);
                    }
                }

                this.blockMesher.endRow();
                this.seondaryblockMesher.endRow();
            }
            this.blockMesher.finish();
            this.seondaryblockMesher.finish();
        }
    }

    private void generateYZFluidOuterGeometry(int axis) {
        this.blockMesher.doAuxiliaryFaceOffset = false;
        //Hacky generate section side faces (without check neighbor section)
        for (int side = 0; side < 2; side++) {//-, +
            int layer = side == 0 ? 0 : 31;
            this.blockMesher.auxiliaryPosition = layer;
            int cSkips = 0;
            for (int other = 0; other < 32; other++) {
                int pidx = axis == 0 ? (layer * 32 + other) : (other * 32 + layer);
                int msk = this.fluidMasks[pidx];
                if (msk == 0) {
                    cSkips += 32;
                    continue;
                }

                this.blockMesher.skip(cSkips);
                cSkips = 0;

                int cIdx = -1;
                while (msk != 0) {
                    int index = Integer.numberOfTrailingZeros(msk);//Is also the x-axis index
                    int delta = index - cIdx - 1;
                    cIdx = index; //index--;
                    if (delta != 0) this.blockMesher.skip(delta);
                    msk &= ~Integer.lowestOneBit(msk);

                    {
                        int idx = index + (pidx * 32);


                        int neighborIdx = ((axis+1)*32*32 * 2)+(side)*32*32;
                        long neighborId = this.neighboringFaces[neighborIdx + (other*32) + index];

                        long A = this.sectionData[idx * 2];
                        long Am = this.sectionData[idx * 2 + 1];
                        int neighborModelId = resolveExternalModelId(neighborId);
                        long neighborMeta = neighborModelId == 0
                                ? 0L
                                : this.modelMan.getModelMetadataFromClientId(neighborModelId);
                        long neighborQuad = packExternalNeighborQuad(neighborModelId, neighborId);
                        meshFluidFace(
                                (axis << 1) | side,
                                A,
                                Am,
                                neighborQuad,
                                neighborMeta,
                                this.blockMesher);
                    }
                }
                this.blockMesher.endRow();
            }
            this.blockMesher.finish();
        }
        this.blockMesher.doAuxiliaryFaceOffset = true;
    }

    private void generateYZNonOpaqueInnerGeometry(int axis) {
        //Note: think is ok to just reuse.. blockMesher
        this.seondaryblockMesher.doAuxiliaryFaceOffset = false;
        this.blockMesher.axis = axis;
        this.seondaryblockMesher.axis = axis;
        for (int layer = 1; layer < 31; layer++) {//(should be 1->31, then have outer face mesher)
            this.blockMesher.auxiliaryPosition = layer;
            this.seondaryblockMesher.auxiliaryPosition = layer;
            int cSkip = 0;
            for (int other = 0; other < 32; other++) {//Section-border faces are emitted by the paired outer pass.
                int pidx = axis == 0 ? (layer * 32 + other) : (other * 32 + layer);
                int skipAmount = axis==0?32*32:32;

                int msk = this.nonOpaqueMasks[pidx];

                if (msk == 0) {
                    cSkip += 32;
                    continue;
                }

                this.blockMesher.skip(cSkip);
                this.seondaryblockMesher.skip(cSkip);
                cSkip = 0;

                int cIdx = -1;
                while (msk != 0) {
                    int index = Integer.numberOfTrailingZeros(msk);//Is also the x-axis index
                    int delta = index - cIdx - 1;
                    cIdx = index; //index--;
                    if (delta != 0) {
                        this.blockMesher.skip(delta);
                        this.seondaryblockMesher.skip(delta);
                    }
                    msk &= ~Integer.lowestOneBit(msk);

                    {
                        int idx = index + (pidx * 32);

                        long A = this.sectionData[idx * 2];
                        long B = this.sectionData[idx * 2+1];

                        meshNonOpaqueFace((axis<<1)|0, A, B, this.sectionData[(idx-skipAmount)*2], this.sectionData[(idx-skipAmount)*2+1], this.seondaryblockMesher);//-
                        meshNonOpaqueFace((axis<<1)|1, A, B, this.sectionData[(idx+skipAmount)*2], this.sectionData[(idx+skipAmount)*2+1], this.blockMesher);//+
                    }
                }
                this.blockMesher.endRow();
                this.seondaryblockMesher.endRow();
            }
            this.blockMesher.finish();
            this.seondaryblockMesher.finish();
        }
    }

    private void generateYZNonOpaqueOuterGeometry(int axis) {
        //Note: think is ok to just reuse.. blockMesher
        this.seondaryblockMesher.doAuxiliaryFaceOffset = false;
        this.blockMesher.axis = axis;
        this.seondaryblockMesher.axis = axis;
        for (int side = 0; side < 2; side++) {//-, +
            int layer = side == 0 ? 0 : 31;
            int skipAmount = (axis==0?32*32:32) * (1-(side*2));
            this.blockMesher.auxiliaryPosition = layer;
            this.seondaryblockMesher.auxiliaryPosition = layer;
            int cSkips = 0;
            for (int other = 0; other < 32; other++) {
                int pidx = axis == 0 ? (layer * 32 + other) : (other * 32 + layer);
                int msk = this.nonOpaqueMasks[pidx];
                if (msk == 0) {
                    cSkips += 32;
                    continue;
                }

                this.blockMesher.skip(cSkips);
                this.seondaryblockMesher.skip(cSkips);
                cSkips = 0;

                int cIdx = -1;
                while (msk != 0) {
                    int index = Integer.numberOfTrailingZeros(msk);//Is also the x-axis index
                    int delta = index - cIdx - 1;
                    cIdx = index; //index--;
                    if (delta != 0) {
                        this.blockMesher.skip(delta);
                        this.seondaryblockMesher.skip(delta);
                    }
                    msk &= ~Integer.lowestOneBit(msk);

                    {
                        int idx = index + (pidx * 32);


                        int neighborIdx = ((axis+1)*32*32 * 2)+(side)*32*32;
                        long neighborId = this.neighboringFaces[neighborIdx + (other*32) + index];

                        long A = this.sectionData[idx * 2];
                        long Am = this.sectionData[idx * 2 + 1];
                        int externalModelId = resolveExternalModelId(neighborId);
                        long externalMeta = externalModelId == 0
                                ? 0L
                                : this.modelMan.getModelMetadataFromClientId(externalModelId);
                        long externalQuad = packExternalNeighborQuad(externalModelId, neighborId);
                        long internalQuad = this.sectionData[(idx+skipAmount) * 2];
                        long internalMeta = this.sectionData[(idx+skipAmount) * 2 + 1];

                        meshNonOpaqueFace(
                                (axis << 1) | 1,
                                A,
                                Am,
                                side == 1 ? externalQuad : internalQuad,
                                side == 1 ? externalMeta : internalMeta,
                                this.blockMesher);
                        meshNonOpaqueFace(
                                axis << 1,
                                A,
                                Am,
                                side == 0 ? externalQuad : internalQuad,
                                side == 0 ? externalMeta : internalMeta,
                                this.seondaryblockMesher);
                    }
                }
                this.blockMesher.endRow();
                this.seondaryblockMesher.endRow();
            }
            this.blockMesher.finish();
            this.seondaryblockMesher.finish();
        }
    }

    private void generateYZFaces() {
        for (int axis = 0; axis < 2; axis++) {//Y then Z
            this.blockMesher.axis = axis;

            this.generateYZOpaqueInnerGeometry(axis);
            this.generateYZOpaqueOuterGeometry(axis);

            this.generateYZFluidInnerGeometry(axis);
            this.generateYZFluidOuterGeometry(axis);
            if (CHECK_NEIGHBOR_FACE_OCCLUSION) {
                this.generateYZNonOpaqueInnerGeometry(axis);
                this.generateYZNonOpaqueOuterGeometry(axis);
            }
        }
    }


    private final Mesher[] xAxisMeshers = new Mesher[32];
    private final Mesher[] secondaryXAxisMeshers = new Mesher[32];
    {
        for (int i = 0; i < 32; i++) {
            var mesher = new Mesher();
            mesher.auxiliaryPosition = i;
            mesher.axis = 2;//X axis
            this.xAxisMeshers[i] = mesher;
        }
        if (CHECK_NEIGHBOR_FACE_OCCLUSION) {
            for (int i = 0; i < 32; i++) {
                var mesher = new Mesher();
                mesher.auxiliaryPosition = i;
                mesher.axis = 2;//X axis
                mesher.doAuxiliaryFaceOffset = false;
                this.secondaryXAxisMeshers[i] = mesher;
            }
        }
    }

    private void generateXOpaqueInnerGeometry() {
        int[] lastProcessedZ = new int[32];
        for (int y = 0; y < 32; y++) {
            Arrays.fill(lastProcessedZ, -1);
            for (int z = 0; z < 32; z++) {
                int lMsk = this.opaqueMasks[y*32+z];
                // The +X outer boundary is generated separately with neighbour-section data.
                int msk = (lMsk ^ (lMsk >>> 1)) & 0x7FFF_FFFF;

                int faceForwardMsk = msk&lMsk;
                int iter = msk;
                while (iter!=0) {
                    int index = Integer.numberOfTrailingZeros(iter);
                    iter &= ~Integer.lowestOneBit(iter);

                    var mesher = this.xAxisMeshers[index];
                    mesher.skip(z - lastProcessedZ[index] - 1);
                    lastProcessedZ[index] = z;

                    int facingForward = ((faceForwardMsk>>index)&1);
                    {
                        int idx = index + (z * 32) + (y * 32 * 32);

                        //Flip data with respect to facing direction
                        int iA = idx * 2 + (facingForward == 1 ? 0 : 2);
                        int iB = idx * 2 + (facingForward == 1 ? 2 : 0);

                        long selfModel = this.sectionData[iA];
                        long selfMeta  = this.sectionData[iA+1];
                        long nextModel = this.sectionData[iB];
                        long nextMeta = this.sectionData[iB + 1];
                        RenderFaceDecision.Result decision = decideBlockFace(
                                (2 << 1) | facingForward,
                                selfModel,
                                selfMeta,
                                nextModel,
                                nextMeta);
                        if (!decision.meshes()) {
                            mesher.skip(1);
                            continue;
                        }

                        mesher.putNext(applyQuadLight(
                                ((long) facingForward) |//Facing
                                (selfModel&~LM) |
                                RenderFaceDecision.selectedLight(decision, selfModel, nextModel),
                                selfMeta
                                )
                        );
                    }
                }
            }
            for (int index = 0; index < 32; index++) {
                this.xAxisMeshers[index].skip(31 - lastProcessedZ[index]);
            }
        }
    }

    private void generateXOuterOpaqueGeometry() {
        //Generate the side faces, hackily, using 0 and 31 mesher

        var ma = this.xAxisMeshers[0];
        var mb = this.xAxisMeshers[31];
        ma.finish();
        mb.finish();
        ma.doAuxiliaryFaceOffset = false;
        mb.doAuxiliaryFaceOffset = false;

        for (int y = 0; y < 32; y++) {
            int skipA = 0;
            int skipB = 0;
            for (int z = 0; z < 32; z++) {
                int i = y*32+z;
                int msk = this.opaqueMasks[i];
                if ((msk & 1) != 0) {//-x
                    long neighborId = this.neighboringFaces[i];
                    long A = this.sectionData[(i<<5) * 2];
                    long Am = this.sectionData[(i<<5) * 2+1];
                    int neighborModelId = resolveExternalModelId(neighborId);
                    long neighborMeta = neighborModelId == 0
                            ? 0L
                            : this.modelMan.getModelMetadataFromClientId(neighborModelId);
                    long neighborQuad = packExternalNeighborQuad(neighborModelId, neighborId);
                    RenderFaceDecision.Result decision = decideBlockFace(
                            2 << 1,
                            A,
                            Am,
                            neighborQuad,
                            neighborMeta);
                    if (decision.meshes()) {
                        ma.skip(skipA); skipA = 0;
                        ma.putNext(applyQuadLight(
                                0L |
                                (A&~LM) |
                                RenderFaceDecision.selectedLight(decision, A, neighborQuad),
                                Am
                                )
                        );
                    } else {skipA++;}
                } else {skipA++;}

                if ((msk & (1<<31)) != 0) {//+x
                    long neighborId = this.neighboringFaces[i+32*32];
                    long A = this.sectionData[(i*32+31) * 2];
                    long Am = this.sectionData[(i*32+31) * 2+1];
                    int neighborModelId = resolveExternalModelId(neighborId);
                    long neighborMeta = neighborModelId == 0
                            ? 0L
                            : this.modelMan.getModelMetadataFromClientId(neighborModelId);
                    long neighborQuad = packExternalNeighborQuad(neighborModelId, neighborId);
                    RenderFaceDecision.Result decision = decideBlockFace(
                            (2 << 1) | 1,
                            A,
                            Am,
                            neighborQuad,
                            neighborMeta);
                    if (decision.meshes()) {
                        mb.skip(skipB); skipB = 0;
                        mb.putNext(applyQuadLight(
                                1L |
                                (A&~LM) |
                                RenderFaceDecision.selectedLight(decision, A, neighborQuad),
                                Am
                                )
                        );
                    } else {skipB++;}
                } else {skipB++;}
            }
            ma.skip(skipA);
            mb.skip(skipB);
        }

        ma.finish();
        mb.finish();
        ma.doAuxiliaryFaceOffset = true;
        mb.doAuxiliaryFaceOffset = true;
    }

    private void generateXInnerFluidGeometry() {
        for (var mesher : this.secondaryXAxisMeshers) {
            mesher.doAuxiliaryFaceOffset = true;
        }
        int[] lastProcessedZ = new int[32];
        for (int y = 0; y < 32; y++) {
            Arrays.fill(lastProcessedZ, -1);
            for (int z = 0; z < 32; z++) {
                int oMsk = this.opaqueMasks[y*32+z];
                int fMsk = this.fluidMasks[y*32+z];
                int lMsk = oMsk|fMsk;
                // The +X outer boundary is generated separately with neighbour-section data.
                int msk = (lMsk ^ (lMsk >>> 1)) & 0x7FFF_FFFF;

                //Dont generate geometry for opaque faces
                msk &= fMsk|(fMsk>>1);
                int differingFluids = differingFluidMaskX(y, z, fMsk & (fMsk >>> 1));
                msk |= differingFluids;

                int faceForwardMsk = msk&lMsk;
                int iter = msk;
                while (iter!=0) {
                    int index = Integer.numberOfTrailingZeros(iter);
                    iter &= ~Integer.lowestOneBit(iter);

                    var mesher = this.xAxisMeshers[index];
                    var secondaryMesher = this.secondaryXAxisMeshers[index];
                    int skipCount = z - lastProcessedZ[index] - 1;
                    mesher.skip(skipCount);
                    secondaryMesher.skip(skipCount);
                    lastProcessedZ[index] = z;

                    int facingForward = ((faceForwardMsk>>index)&1);
                    {
                        int idx = index + (z * 32) + (y * 32 * 32);

                        if ((differingFluids & (1 << index)) != 0) {
                            int first = idx * 2;
                            int second = (idx + 1) * 2;
                            meshFluidFace(
                                    (2 << 1) | 1,
                                    this.sectionData[first],
                                    this.sectionData[first + 1],
                                    this.sectionData[second],
                                    this.sectionData[second + 1],
                                    mesher);
                            meshFluidFace(
                                    2 << 1,
                                    this.sectionData[second],
                                    this.sectionData[second + 1],
                                    this.sectionData[first],
                                    this.sectionData[first + 1],
                                    secondaryMesher);
                            continue;
                        }

                        //The facingForward thing is to get next entry automajicly
                        int ai = (idx+(1-facingForward))*2;
                        int bi = (idx+facingForward)*2;

                        long A = this.sectionData[ai];
                        long Am = this.sectionData[ai+1];
                        meshFluidFace(
                                (2 << 1) | facingForward,
                                A,
                                Am,
                                this.sectionData[bi],
                                this.sectionData[bi + 1],
                                mesher);
                        secondaryMesher.skip(1);
                    }
                }
            }
            for (int index = 0; index < 32; index++) {
                int skipCount = 31 - lastProcessedZ[index];
                this.xAxisMeshers[index].skip(skipCount);
                this.secondaryXAxisMeshers[index].skip(skipCount);
            }
        }
    }

    private void generateXOuterFluidGeometry() {
        //Generate the side faces, hackily, using 0 and 31 mesher

        var ma = this.xAxisMeshers[0];
        var mb = this.xAxisMeshers[31];
        ma.finish();
        mb.finish();
        ma.doAuxiliaryFaceOffset = false;
        mb.doAuxiliaryFaceOffset = false;

        for (int y = 0; y < 32; y++) {
            int skipA = 0;
            int skipB = 0;
            for (int z = 0; z < 32; z++) {
                int i = y*32+z;
                int msk = this.fluidMasks[i];
                if ((msk & 1) != 0) {//-x
                    long neighborId = this.neighboringFaces[i];

                    int sidx = (i<<5) * 2;
                    long A = this.sectionData[sidx];
                    long Am = this.sectionData[sidx + 1];
                    int neighborModelId = resolveExternalModelId(neighborId);
                    long neighborMeta = neighborModelId == 0
                            ? 0L
                            : this.modelMan.getModelMetadataFromClientId(neighborModelId);
                    long neighborQuad = packExternalNeighborQuad(neighborModelId, neighborId);
                    ma.skip(skipA); skipA = 0;
                    meshFluidFace(
                            2 << 1,
                            A,
                            Am,
                            neighborQuad,
                            neighborMeta,
                            ma);
                } else {skipA++;}

                if ((msk & (1<<31)) != 0) {//+x
                    long neighborId = this.neighboringFaces[i+32*32];

                    int sidx = (i*32+31) * 2;
                    long A = this.sectionData[sidx];
                    long Am = this.sectionData[sidx + 1];
                    int neighborModelId = resolveExternalModelId(neighborId);
                    long neighborMeta = neighborModelId == 0
                            ? 0L
                            : this.modelMan.getModelMetadataFromClientId(neighborModelId);
                    long neighborQuad = packExternalNeighborQuad(neighborModelId, neighborId);
                    mb.skip(skipB); skipB = 0;
                    meshFluidFace(
                            (2 << 1) | 1,
                            A,
                            Am,
                            neighborQuad,
                            neighborMeta,
                            mb);
                } else {skipB++;}
            }
            ma.skip(skipA);
            mb.skip(skipB);
        }

        ma.finish();
        mb.finish();
        ma.doAuxiliaryFaceOffset = true;
        mb.doAuxiliaryFaceOffset = true;
    }

    private void generateXNonOpaqueInnerGeometry() {
        int[] lastProcessedZ = new int[32];
        for (int y = 0; y < 32; y++) {
            Arrays.fill(lastProcessedZ, -1);
            for (int z = 0; z < 32; z++) {
                int msk = this.nonOpaqueMasks[y*32+z]&(~0x80000001);//Dont mesh the outer layer

                int iter = msk;
                while (iter!=0) {
                    int index = Integer.numberOfTrailingZeros(iter);
                    iter &= ~Integer.lowestOneBit(iter);

                    var mesherA = this.xAxisMeshers[index];
                    var mesherB = this.secondaryXAxisMeshers[index];
                    int skipCount = z - lastProcessedZ[index] - 1;
                    mesherA.skip(skipCount);
                    mesherB.skip(skipCount);
                    lastProcessedZ[index] = z;

                    {
                        int idx = index + (z * 32) + (y * 32 * 32);

                        long A = this.sectionData[idx*2];
                        long Am = this.sectionData[idx*2+1];

                        //Check and generate the mesh for both + and - faces
                        meshNonOpaqueFace(2<<1, A, Am, this.sectionData[(idx-1)*2], this.sectionData[(idx-1)*2+1], mesherB);//-
                        meshNonOpaqueFace((2<<1)|1, A, Am, this.sectionData[(idx+1)*2], this.sectionData[(idx+1)*2+1], mesherA);//+
                    }
                }
            }
            for (int index = 0; index < 32; index++) {
                int skipCount = 31 - lastProcessedZ[index];
                this.xAxisMeshers[index].skip(skipCount);
                this.secondaryXAxisMeshers[index].skip(skipCount);
            }
        }
    }



    private void dualMeshNonOpaqueOuterX(int side, long quad, long meta, int neighborAId, int neighborLight, long neighborAMeta, long neighborBQuad, long neighborBMeta, Mesher ma, Mesher mb) {
        //side == 0 if is on 0 side and 1 if on 31 side

        long neighborAQuad = ((long) neighborAId << 26) | ((long) neighborLight << 55);
        RenderFaceDecision.Result decisionA = decideBlockFace(
                ((2 << 1) | 0) ^ side,
                quad,
                meta,
                neighborAQuad,
                neighborAMeta);
        if (decisionA.meshes()) {
            ma.putNext(applyQuadLight(
                    ((long)side)|
                    (quad&~LM) |
                    RenderFaceDecision.selectedLight(decisionA, quad, neighborAQuad),
                    meta
                    )
            );
        } else {
            ma.skip(1);
        }

        RenderFaceDecision.Result decisionB = decideBlockFace(
                ((2 << 1) | 1) ^ side,
                quad,
                meta,
                neighborBQuad,
                neighborBMeta);
        if (decisionB.meshes()) {
            mb.putNext(applyQuadLight(
                    ((long)(side^1))|
                    (quad&~LM) |
                    RenderFaceDecision.selectedLight(decisionB, quad, neighborBQuad),
                    meta
                    )
            );
        } else {
            mb.skip(1);
        }
    }

    private void generateXNonOpaqueOuterGeometry() {
        var npx = this.xAxisMeshers[0]; npx.finish();
        var nnx = this.secondaryXAxisMeshers[0]; nnx.finish();
        var ppx = this.xAxisMeshers[31]; ppx.finish();
        var pnx = this.secondaryXAxisMeshers[31]; pnx.finish();

        for (int y = 0; y < 32; y++) {
            int skipA = 0;
            int skipB = 0;
            for (int z = 0; z < 32; z++) {
                int i = y*32+z;
                int msk = this.nonOpaqueMasks[i];
                if ((msk & 1) != 0) {//-x
                    long neighborId = this.neighboringFaces[i];

                    int sidx = (i<<5) * 2;
                    long A = this.sectionData[sidx];
                    long Am = this.sectionData[sidx + 1];

                    int modelId = 0;
                    long nM = 0;
                    if (Mapper.getBlockId(neighborId) != 0) {//Not air
                        modelId = this.modelMan.getModelId(Mapper.getBlockId(neighborId));
                        nM = this.modelMan.getModelMetadataFromClientId(modelId);
                    }

                    nnx.skip(skipA);
                    npx.skip(skipA); skipA = 0;

                    dualMeshNonOpaqueOuterX(0, A, Am, modelId, Mapper.getLightId(neighborId), nM, this.sectionData[sidx+2], this.sectionData[sidx+3], nnx, npx);
                } else {skipA++;}

                if ((msk & (1<<31)) != 0) {//+x
                    long neighborId = this.neighboringFaces[i+32*32];

                    int sidx = (i*32+31) * 2;
                    long A = this.sectionData[sidx];
                    long Am = this.sectionData[sidx + 1];

                    int modelId = 0;
                    long nM = 0;
                    if (Mapper.getBlockId(neighborId) != 0) {//Not air
                        modelId = this.modelMan.getModelId(Mapper.getBlockId(neighborId));
                        nM = this.modelMan.getModelMetadataFromClientId(modelId);
                    }

                    pnx.skip(skipB);
                    ppx.skip(skipB); skipB = 0;

                    dualMeshNonOpaqueOuterX(1, A, Am, modelId, Mapper.getLightId(neighborId), nM, this.sectionData[sidx-2], this.sectionData[sidx-1], ppx, pnx);
                } else {skipB++;}
            }
            nnx.skip(skipA);
            npx.skip(skipA);
            pnx.skip(skipB);
            ppx.skip(skipB);
        }
    }

    private void generateXFaces() {
        this.generateXOpaqueInnerGeometry();
        this.generateXOuterOpaqueGeometry();

        for (var mesher : this.xAxisMeshers) {
            mesher.finish();
        }

        this.generateXInnerFluidGeometry();
        this.generateXOuterFluidGeometry();

        for (var mesher : this.xAxisMeshers) {
            mesher.finish();
        }
        for (var mesher : this.secondaryXAxisMeshers) {
            mesher.finish();
            mesher.doAuxiliaryFaceOffset = false;
        }
        if (CHECK_NEIGHBOR_FACE_OCCLUSION) {
            this.generateXNonOpaqueInnerGeometry();
            this.generateXNonOpaqueOuterGeometry();

            for (var mesher : this.xAxisMeshers) {
                mesher.finish();
            }
            for (var mesher : this.secondaryXAxisMeshers) {
                mesher.finish();
            }
        }
    }

    private final int occupancyBarrier(int index) {
        int occ = 0;
        int msk = this.opaqueMasks[index];
        //x
        occ |= msk^(msk>>1);
        occ |= msk^(msk<<1);
        //y
        occ |= index<32*31?msk^this.opaqueMasks[index+32]:0;
        occ |= 31<index   ?msk^this.opaqueMasks[index-32]:0;
        //z
        occ |= (index&31)<31?msk^this.opaqueMasks[index+1]:0;
        occ |= 0< (index&31)?msk^this.opaqueMasks[index-1]:0;
        return occ;
    }

    //Build the occupancy set (used for AO) from the set of fully opaque blocks (atm, this can change in the future if needed to a special occupancy bitset)
    private final void buildOccupancy() {
        //We basicly want to record all the points where we go from air to solid or solid to air (this is to just get better compression)
        for (int i = 0; i < 32*32; i++) {
            int occ = this.occupancyBarrier(i);
            //We now have our occlusion mask, fill in our occupancy set
            for (;occ!=0;occ&=~Integer.lowestOneBit(occ)) {
                this.occupancy.set(i*32+Integer.numberOfTrailingZeros(occ));
            }
        }
    }

    private final void buildOccupancy16() {
        //We basicly want to record all the points where we go from air to solid or solid to air (this is to just get better compression)
        for (int i = 0; i < 16*16; i++) {
            int x = (i&15)*2;
            int y = (i>>4)*2;
            int A = this.occupancyBarrier(y*32+x); A = (A|(A>>16))&0xFFFF;
            int B = this.occupancyBarrier(y*32+x+1); B = (B|(B>>16))&0xFFFF;
            int C = this.occupancyBarrier((y+1)*32+x); C = (C|(C>>16))&0xFFFF;
            int D = this.occupancyBarrier((y+1)*32+x+1); D = (D|(D>>16))&0xFFFF;
            int occ = A|B|C|D;

            //Shink to 16 bit
            //We now have our occlusion mask, fill in our occupancy set
            for (;occ!=0;occ&=~Integer.lowestOneBit(occ)) {
                this.occupancy.set(i*16+Integer.numberOfTrailingZeros(occ));
            }
        }
    }

    //section is already acquired and gets released by the parent
    public BuiltSection generateMesh(WorldSection section) {
        // A worker-local factory is reused after missing-model failures, so reset every mutable
        // meshing owner before reading any part of the next section.
        this.quadCount = 0;

        {//Reset all the block meshes
            this.blockMesher.reset();
            this.blockMesher.doAuxiliaryFaceOffset = true;
            this.seondaryblockMesher.reset();
            this.seondaryblockMesher.doAuxiliaryFaceOffset = true;
            for (var mesher : this.xAxisMeshers) {
                mesher.reset();
                mesher.doAuxiliaryFaceOffset = true;
            }
            if (CHECK_NEIGHBOR_FACE_OCCLUSION) {
                for (var mesher : this.secondaryXAxisMeshers) {
                    mesher.reset();
                    mesher.doAuxiliaryFaceOffset = false;
                }
            }
        }
        if (this.occupancy != null) {
            this.occupancy.reset();
        }

        this.minX = Integer.MAX_VALUE;
        this.minY = Integer.MAX_VALUE;
        this.minZ = Integer.MAX_VALUE;
        this.maxX = Integer.MIN_VALUE;
        this.maxY = Integer.MIN_VALUE;
        this.maxZ = Integer.MIN_VALUE;

        Arrays.fill(this.quadCounters,0);
        Arrays.fill(this.opaqueMasks, 0);
        Arrays.fill(this.nonOpaqueMasks, 0);
        Arrays.fill(this.fluidMasks, 0);

        //Prepare everything
        int neighborMskAndFlags = this.prepareSectionData(section._unsafeGetRawDataArray());
        if ((neighborMskAndFlags&(1<<31))!=0) {//We failed to get everything so throw exception
            throw new IdNotYetComputedException(neighborMskAndFlags&((1<<20)-1), true);
        }
        int neighborMsk = neighborMskAndFlags&0b11_11_11;
        int flags = neighborMskAndFlags>>>6;
        if (CHECK_NEIGHBOR_FACE_OCCLUSION) {
            this.acquireNeighborData(section, neighborMsk);
        }

        try {
            this.generateYZFaces();
            this.generateXFaces();
        } catch (IdNotYetComputedException e) {
            e.auxBitMsk = neighborMsk;
            e.auxData = this.neighboringFaces;
            throw e;
        }

        //We only care if we have quads
        if (this.occupancy != null && section.lvl == 0 /*only generate occupancy for lowest lod level*/ && this.quadCount != 0 && (flags&1) != 0) {
            this.buildOccupancy();
        }

        if (this.quadCount == 0) {
            return BuiltSection.emptyWithChildren(section.key, section.getNonEmptyChildren());
        }

        if (this.quadCount >= 1<<16) {
            Logger.warn("Large quad count for section " + WorldEngine.pprintPos(section.key) + " is " + this.quadCount);
        }

        if (this.minX < 0 || this.minY < 0 || this.minZ < 0
                || this.maxX < this.minX || this.maxY < this.minY || this.maxZ < this.minZ
                || 32 < this.maxX || 32 < this.maxY || 32 < this.maxZ) {
            throw new IllegalStateException(
                    "invalid-render-aabb: [" + this.minX + "," + this.minY + "," + this.minZ
                            + "]..[" + this.maxX + "," + this.maxY + "," + this.maxZ + "]");
        }

        int[] offsets = new int[QUAD_BUCKET_COUNT];
        var buff = new MemoryBuffer(this.quadCount * (long) Long.BYTES);
        long ptr = buff.address;
        int coff = 0;
        for (int buffer = 0; buffer < QUAD_BUCKET_COUNT; buffer++) {// translucent, double sided quads, 6 faces
            offsets[buffer] = coff;
            int size = this.quadCounters[buffer];
            UnsafeUtil.memcpy(
                    this.quadBufferPtr + (long) buffer * Long.BYTES * QUADS_PER_BUCKET,
                    ptr + (long) coff * Long.BYTES,
                    (long) size * Long.BYTES);
            coff += size;
        }
        if (coff != this.quadCount) {
            buff.free();
            throw new IllegalStateException(
                    "render-quad-count-mismatch: buckets=" + coff + ", total=" + this.quadCount);
        }
        int aabb = 0;
        aabb |= this.minX;
        aabb |= this.minY<<5;
        aabb |= this.minZ<<10;
        aabb |= Math.max(0,this.maxX-this.minX-1)<<15;
        aabb |= Math.max(0,this.maxY-this.minY-1)<<20;
        aabb |= Math.max(0,this.maxZ-this.minZ-1)<<25;

        MemoryBuffer occupancy = null;
        if (this.occupancy != null && !this.occupancy.isEmpty()) {
            occupancy = new MemoryBuffer(this.occupancy.writeSize());
            this.occupancy.write(occupancy.address, false);
        }

        return new BuiltSection(section.key, section.getNonEmptyChildren(), aabb, buff, offsets, occupancy);
    }

    private static int expandBits(int value, int mask) {
        int result = 0;
        int inputBit = 0;
        for (int bit = 0; bit < Integer.SIZE; bit++) {
            int bitMask = 1 << bit;
            if ((mask & bitMask) != 0) {
                if ((value & (1 << inputBit)) != 0) {
                    result |= bitMask;
                }
                inputBit++;
            }
        }
        return result;
    }

    public void free() {
        this.quadBuffer.free();
    }
}
