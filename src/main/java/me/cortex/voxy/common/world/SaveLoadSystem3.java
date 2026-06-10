package me.cortex.voxy.common.world;

import it.unimi.dsi.fastutil.longs.Long2ShortOpenHashMap;
import me.cortex.voxy.common.util.MemoryBuffer;
import me.cortex.voxy.common.util.ThreadLocalMemoryBuffer;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.LoggerFactory;

public class SaveLoadSystem3 {
    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger("Voxy");
    public static final int STORAGE_VERSION = 0;

    private record SerializationCache(Long2ShortOpenHashMap lutMapCache, MemoryBuffer memoryBuffer) {
        public SerializationCache() {
            this(new Long2ShortOpenHashMap(1024), ThreadLocalMemoryBuffer.create(WorldSection.SECTION_VOLUME*2+WorldSection.SECTION_VOLUME*8+1024));
            this.lutMapCache.defaultReturnValue((short) -1);
        }
    }
    public static int lin2z(int i) {//y,z,x
        int x = i&0x1F;
        int y = (i>>10)&0x1F;
        int z = (i>>5)&0x1F;
        return expand3(x, 0)|expand3(y, 1)|expand3(z, 2);

        //zyxzyxzyxzyxzyx
    }

    public static int z2lin(int i) {
        int x = compact3(i, 0);
        int y = compact3(i, 1);
        int z = compact3(i, 2);
        return x|(y<<10)|(z<<5);
    }

    private static int expand3(int value, int offset) {
        int result = 0;
        for (int bit = 0; bit < 5; bit++) {
            result |= ((value >> bit) & 1) << (bit * 3 + offset);
        }
        return result;
    }

    private static int compact3(int value, int offset) {
        int result = 0;
        for (int bit = 0; bit < 5; bit++) {
            result |= ((value >> (bit * 3 + offset)) & 1) << bit;
        }
        return result;
    }

    private static boolean isAir(long id) {
        return (id&(((1L<<20)-1)<<27)) == 0;
    }

    private static final ThreadLocal<SerializationCache> CACHE = ThreadLocal.withInitial(SerializationCache::new);

    //TODO: Cache like long2short and the short and other data to stop allocs
    public static MemoryBuffer serialize(WorldSection section) {
        var cache = CACHE.get();
        var data = section.data;

        Long2ShortOpenHashMap LUT = cache.lutMapCache; LUT.clear();

        MemoryBuffer buffer = cache.memoryBuffer().createUntrackedUnfreeableReference();
        long ptr = buffer.address;

        MemoryUtil.memPutLong(ptr, section.key); ptr += 8;
        long metadataPtr = ptr; ptr += 8;

        long blockPtr = ptr; ptr += WorldSection.SECTION_VOLUME*2;
        long prev = data[0]; MemoryUtil.memPutLong(ptr, prev); ptr+=8; LUT.put(prev, (short) 0);
        short mapping = 0;
        for (long block : data) {
            if (prev != block) {
                prev = block;
                mapping = LUT.putIfAbsent(block, (short) LUT.size());
                if (mapping == -1) {
                    mapping = (short) (LUT.size()-1);
                    MemoryUtil.memPutLong(ptr, block); ptr+=8;
                }
            }
            MemoryUtil.memPutShort(blockPtr, mapping); blockPtr+=2;
        }
        if (LUT.size() >= 1<<16) {
            throw new IllegalStateException();
        }

        //TODO: note! can actually have the first (last?) byte of metadata be the storage version!
        long metadata = 0;
        metadata |= Integer.toUnsignedLong(LUT.size());//Bottom 2 bytes
        metadata |= Byte.toUnsignedLong(section.getNonEmptyChildren())<<16;//Next byte
        //5 bytes free

        MemoryUtil.memPutLong(metadataPtr, metadata);
        //TODO: do hash

        return buffer.subSize(ptr-buffer.address);//Does not get freed
    }

    public static boolean deserialize(WorldSection section, MemoryBuffer data) {
        long ptr = data.address;
        long key = MemoryUtil.memGetLong(ptr); ptr += 8;

        if (section.key != key) {
            //throw new IllegalStateException("Decompressed section not the same as requested. got: " + key + " expected: " + section.key);
            LOGGER.error("Decompressed section not the same as requested. got: {} expected: {}", key, section.key);
            return false;
        }

        final long metadata = MemoryUtil.memGetLong(ptr); ptr += 8;
        section.nonEmptyChildren = (byte) ((metadata>>>16)&0xFF);
        final long lutBasePtr = ptr + WorldSection.SECTION_VOLUME * 2;

        final var blockData = section.data;
        for (int i = 0; i < WorldSection.SECTION_VOLUME; i++) {
            blockData[i] = MemoryUtil.memGetLong(lutBasePtr + Short.toUnsignedLong(MemoryUtil.memGetShort(ptr)) * 8L);ptr += 2;
        }

        if (section.lvl == 0) {
            int emptyBlockCount = 0;
            for (long block : blockData) {
                emptyBlockCount += isAir(block) ? 1 : 0;
            }
            section.nonEmptyBlockCount = WorldSection.SECTION_VOLUME-emptyBlockCount;
        }

        ptr = lutBasePtr + (metadata & 0xFFFF) * 8L;
        return true;
    }
}
