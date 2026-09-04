package me.cortex.voxy.common.world;

import me.cortex.voxy.common.Logger;
import me.cortex.voxy.common.config.section.SectionStorage;
import me.cortex.voxy.common.world.other.Mipper;
import me.cortex.voxy.common.util.TrackedObject;
import me.cortex.voxy.common.world.other.Mapper;

import java.lang.invoke.VarHandle;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class WorldEngine {
    public static final int MAX_LOD_LAYER = 4;

    public static final int UPDATE_TYPE_BLOCK_BIT = 1;
    public static final int UPDATE_TYPE_CHILD_EXISTENCE_BIT = 2;
    public static final int UPDATE_TYPE_DONT_SAVE = 4;
    public static final int DEFAULT_UPDATE_FLAGS = UPDATE_TYPE_BLOCK_BIT | UPDATE_TYPE_CHILD_EXISTENCE_BIT;

    public interface ISectionChangeCallback {void accept(WorldSection section, int updateFlags, int neighborMsk);}
    public interface ISectionSaveCallback {boolean save(WorldEngine engine, WorldSection section, boolean nonBlocking, boolean sectionAlreadyAcquired);}

    private final TrackedObject thisTracker = TrackedObject.createTrackedObject(this);

    public final SectionStorage storage;
    private final Mapper mapper;
    private final ActiveSectionTracker sectionTracker;
    private ISectionChangeCallback dirtyCallback;
    private ISectionSaveCallback saveCallback;
    volatile boolean isLive = true;

    public void setDirtyCallback(ISectionChangeCallback callback) {
        this.dirtyCallback = callback;
    }

    public void setSaveCallback(ISectionSaveCallback callback) {
        this.saveCallback = callback;
    }

    public Mapper getMapper() {return this.mapper;}
    public boolean isLive() {return this.isLive;}

    /** Narrow lifecycle contract used by work owned by one Voxy session. */
    public interface LifecycleOwner {
        boolean isRunning();
    }

    public final Object instanceIn;
    private final LifecycleOwner lifecycleOwner;
    private final AtomicInteger refCount = new AtomicInteger();
    volatile long lastActiveTime = System.currentTimeMillis();//Time in millis the world was last "active" i.e. had a total ref count or active section count of != 0

    public WorldEngine(SectionStorage storage) {
        this(storage, (Object) null);
    }

    public WorldEngine(SectionStorage storage, Object instance) {
        this(storage, instance, null);
    }

    public WorldEngine(SectionStorage storage, LifecycleOwner instance) {
        this(storage, instance, instance);
    }

    private WorldEngine(SectionStorage storage, Object instance, LifecycleOwner lifecycleOwner) {
        this.instanceIn = instance;
        this.lifecycleOwner = lifecycleOwner;

        int cacheSize = 1024;
        if (Runtime.getRuntime().maxMemory()>=(1L<<32)-(200L<<20)) {
            cacheSize = 2048;
        }

        this.storage = storage;
        this.mapper = new Mapper(this.storage);
        //5 cache size bits means that the section tracker has 32 separate maps that it uses
        this.sectionTracker = new ActiveSectionTracker(6, this::loadSection, cacheSize, this);
    }

    private int loadSection(WorldSection section) {
        int status = this.storage.loadSection(section);
        if (status != SectionStorage.LOAD_CORRUPT) {
            return status;
        }
        if (section.lvl == 0) {
            Logger.error("Corrupt level-zero section is unavailable and was not replaced with invented data: "
                    + pprintPos(section.key));
            return SectionStorage.LOAD_UNAVAILABLE;
        }
        return this.recoverSectionFromChildren(section);
    }

    private int recoverSectionFromChildren(WorldSection parent) {
        WorldSection[] children = new WorldSection[8];
        try {
            byte nonEmptyChildren = 0;
            for (int childY = 0; childY < 2; childY++) {
                for (int childZ = 0; childZ < 2; childZ++) {
                    for (int childX = 0; childX < 2; childX++) {
                        int childIndex = WorldSection.getChildIndex(childX, childY, childZ);
                        WorldSection child = this.sectionTracker.acquire(
                                parent.lvl - 1,
                                parent.x * 2 + childX,
                                parent.y * 2 + childY,
                                parent.z * 2 + childZ,
                                true);
                        if (child == null) {
                            Logger.error("Corrupt section " + pprintPos(parent.key)
                                    + " cannot be regenerated because child " + childIndex + " is unavailable");
                            return SectionStorage.LOAD_UNAVAILABLE;
                        }
                        children[childIndex] = child;
                        if (child.getNonEmptyChildren() != 0) {
                            nonEmptyChildren |= (byte) (1 << childIndex);
                        }
                    }
                }
            }

            long[] output = parent._unsafeGetRawDataArray();
            for (int y = 0; y < 32; y++) {
                for (int z = 0; z < 32; z++) {
                    for (int x = 0; x < 32; x++) {
                        int sourceX = x << 1;
                        int sourceY = y << 1;
                        int sourceZ = z << 1;
                        output[WorldSection.getIndex(x, y, z)] = Mipper.mip(
                                childValue(children, sourceX, sourceY, sourceZ),
                                childValue(children, sourceX + 1, sourceY, sourceZ),
                                childValue(children, sourceX, sourceY, sourceZ + 1),
                                childValue(children, sourceX + 1, sourceY, sourceZ + 1),
                                childValue(children, sourceX, sourceY + 1, sourceZ),
                                childValue(children, sourceX + 1, sourceY + 1, sourceZ),
                                childValue(children, sourceX, sourceY + 1, sourceZ + 1),
                                childValue(children, sourceX + 1, sourceY + 1, sourceZ + 1),
                                this.mapper,
                                parent.lvl);
                    }
                }
            }
            parent._unsafeSetNonEmptyChildren(nonEmptyChildren);
            this.storage.saveSection(parent);
            this.storage.flush();
            Logger.warn("Regenerated corrupt section from eight verified children: " + pprintPos(parent.key));
            return SectionStorage.LOAD_RECOVERED;
        } finally {
            for (WorldSection child : children) {
                if (child != null) {
                    child.release();
                }
            }
        }
    }

    private static long childValue(WorldSection[] children, int x, int y, int z) {
        int childIndex = WorldSection.getChildIndex(x >>> 5, y >>> 5, z >>> 5);
        return children[childIndex]._unsafeGetRawDataArray()[WorldSection.getIndex(x & 31, y & 31, z & 31)];
    }

    public boolean isOwningSessionRunning() {
        return this.lifecycleOwner == null || this.lifecycleOwner.isRunning();
    }

    public WorldSection acquireIfExists(int lvl, int x, int y, int z) {
        if (!this.isLive) throw new IllegalStateException("World is not live");
        return this.sectionTracker.acquire(lvl, x, y, z, true);
    }

    public WorldSection acquire(int lvl, int x, int y, int z) {
        if (!this.isLive) throw new IllegalStateException("World is not live");
        return this.sectionTracker.acquire(lvl, x, y, z, false);
    }

    public WorldSection acquire(long pos) {
        if (!this.isLive) throw new IllegalStateException("World is not live");
        return this.sectionTracker.acquire(pos, false);
    }

    public WorldSection acquireIfExists(long pos) {
        if (!this.isLive) throw new IllegalStateException("World is not live");
        return this.sectionTracker.acquire(pos, true);
    }

    public static final int POS_FORMAT_VERSION = 1;

    //TODO: Fixme/optimize, cause as the lvl gets higher, the size of x,y,z gets smaller so i can dynamically compact the format
    // depending on the lvl, which should optimize colisions and whatnot
    public static long getWorldSectionId(int lvl, int x, int y, int z) {
        return ((long)lvl<<60)|((long)(y&0xFF)<<52)|((long)(z&((1<<24)-1))<<28)|((long)(x&((1<<24)-1))<<4);//NOTE: 4 bits spare for whatever
    }

    public static int getLevel(long id) {
        return (int) ((id>>60)&0xf);
    }
    public static int getX(long id) {
        return (int) ((id<<36)>>40);
    }

    public static int getY(long id) {
        return (int) ((id<<4)>>56);
    }

    public static int getZ(long id) {
        return (int) ((id<<12)>>40);
    }

    public static String pprintPos(long pos) {
        return getLevel(pos)+"@["+getX(pos)+", "+getY(pos)+", " + getZ(pos)+"]";
    }

    //Marks a section as dirty, enqueuing it for saving and or render data rebuilding
    public void markDirty(WorldSection section) {
        this.markDirty(section, DEFAULT_UPDATE_FLAGS, 0);
    }

    public void markDirty(WorldSection section, int changeState, int neighborMsk) {
        if (!this.isLive) throw new IllegalStateException("World is not live");
        if (section.tracker != this.sectionTracker) {
            throw new IllegalStateException("Section is not from here");
        }
        if ((changeState & DEFAULT_UPDATE_FLAGS) != 0
                && section.getStorageLoadStatus() == SectionStorage.LOAD_MISSING) {
            // A disk miss describes the initial load, not the lifetime of this object.
            // A real voxel/child update materializes it in memory. Publish that before
            // notifying mesh consumers, whose acquireIfExists must see the new data.
            // Unavailable/corrupt storage stays fail-closed; this is not recovery.
            section._setStorageLoadStatus(SectionStorage.LOAD_OK);
        }
        if (this.dirtyCallback != null) {
            this.dirtyCallback.accept(section, changeState, neighborMsk);
        }
        if ((changeState&UPDATE_TYPE_DONT_SAVE)==0) {
            section.markDirty();
        }
    }

    public void addDebugData(List<String> debug) {
        debug.add("ACC/SCC: " + this.sectionTracker.getLoadedCacheCount()+"/"+this.sectionTracker.getSecondaryCacheSize());//Active cache count, Secondary cache counts
    }

    public int getActiveSectionCount() {
        return this.sectionTracker.getLoadedCacheCount();
    }

    public String round6SectionTrackerPerformanceSummary() {
        return "loaderWaits=" + this.sectionTracker.getLoaderWaitCount()
                + ", loaderWaitNanos=" + this.sectionTracker.getLoaderWaitNanos()
                + ", secondaryHits=" + this.sectionTracker.getSecondaryCacheHits()
                + ", secondaryMisses=" + this.sectionTracker.getSecondaryCacheMisses()
                + ", secondaryEvictions=" + this.sectionTracker.getSecondaryCacheEvictions();
    }

    public void free() {
        if (!this.isLive) throw new IllegalStateException();
        this.isLive = false;
        VarHandle.fullFence();
        //Cannot free while there are loaded sections
        if (this.sectionTracker.getLoadedCacheCount() != 0) {
            throw new IllegalStateException();
        }

        this.thisTracker.free();
        try {this.mapper.close();} catch (Exception e) {Logger.error(e);}
        try {this.storage.flush();} catch (Exception e) {Logger.error(e);}
        //Shutdown in this order to preserve as much data as possible
        try {this.storage.close();} catch (Exception e) {Logger.error(e);}
    }

    private static final long TIMEOUT_MILLIS = 10_000;//10 second timeout (is to long? or to short??)
    public boolean isWorldUsed() {
        if (!this.isLive) throw new IllegalStateException();
        return this.refCount.get() != 0 || this.sectionTracker.getLoadedCacheCount() != 0;
    }

    public boolean isWorldIdle() {
        if (this.isWorldUsed()) {
            this.lastActiveTime = System.currentTimeMillis();//Force an update if is not active
            VarHandle.fullFence();
            return false;
        }
        return TIMEOUT_MILLIS<(System.currentTimeMillis()-this.lastActiveTime);
    }

    public void markActive() {
        if (!this.isLive) throw new IllegalStateException();
        this.lastActiveTime = System.currentTimeMillis();
    }

    public void acquireRef() {
        if (!this.isLive) throw new IllegalStateException();
        this.refCount.incrementAndGet();
        this.lastActiveTime = System.currentTimeMillis();
    }

    public void releaseRef() {
        if (!this.isLive) throw new IllegalStateException();
        if (this.refCount.decrementAndGet()<0) {
            throw new IllegalStateException("ref count less than 0");
        }
        //TODO: maybe dont need to tick the last active time?
        this.lastActiveTime = System.currentTimeMillis();
    }

    public boolean saveSection(WorldSection section) {
        return this.saveSection(section, false, false);
    }

    public boolean saveSection(WorldSection section, boolean nonBlocking, boolean sectionAlreadyAcquired) {
        if (this.saveCallback != null) {
            return this.saveCallback.save(this, section, nonBlocking, sectionAlreadyAcquired);
        }
        return false;
    }
}
