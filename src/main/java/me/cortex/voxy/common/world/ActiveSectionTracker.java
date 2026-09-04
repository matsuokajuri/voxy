package me.cortex.voxy.common.world;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import me.cortex.voxy.common.Logger;
import me.cortex.voxy.common.config.section.SectionStorage;
import me.cortex.voxy.common.world.other.Mapper;

import java.lang.invoke.VarHandle;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.StampedLock;

public class ActiveSectionTracker implements WorldSection.ReleaseTracker {
    //Deserialize into the supplied section, returns true on success, false on failure
    public interface SectionLoader {int load(WorldSection section);}

    private static final class SectionHolder {
        private final CompletableFuture<WorldSection> completion = new CompletableFuture<>();
        private int pendingAcquires;
        volatile WorldSection obj;

        synchronized boolean reservePendingAcquire() {
            if (this.obj != null || this.completion.isDone()) {
                return false;
            }
            this.pendingAcquires++;
            return true;
        }

        synchronized void publish(WorldSection section) {
            if (this.obj != null || this.completion.isDone()) {
                throw new IllegalStateException("section-holder-already-completed");
            }
            section.acquire(this.pendingAcquires);
            this.obj = section;
            this.completion.complete(section);
        }

        void fail(Throwable throwable) {
            this.completion.completeExceptionally(throwable);
        }

        WorldSection await() {
            return this.completion.join();
        }
    }

    private final AtomicInteger loadedSections = new AtomicInteger();
    private final AtomicInteger secondaryCachedSections = new AtomicInteger();
    private final Long2ObjectOpenHashMap<SectionHolder>[] loadedSectionCache;
    private final Long2ObjectLinkedOpenHashMap<WorldSection>[] secondaryCaches;
    private final int[] secondaryCacheLimits;
    private final StampedLock[] locks;
    private final SectionLoader loader;
    private final LongAdder loaderWaitCount = new LongAdder();
    private final LongAdder loaderWaitNanos = new LongAdder();
    private final LongAdder secondaryCacheHits = new LongAdder();
    private final LongAdder secondaryCacheMisses = new LongAdder();
    private final LongAdder secondaryCacheEvictions = new LongAdder();

    public final WorldEngine engine;

    public ActiveSectionTracker(int numSlicesBits, SectionLoader loader, int cacheSize) {
        this(numSlicesBits, loader, cacheSize, null);
    }

    @SuppressWarnings("unchecked")
    public ActiveSectionTracker(int numSlicesBits, SectionLoader loader, int cacheSize, WorldEngine engine) {
        this.engine = engine;

        this.loader = loader;
        this.loadedSectionCache = new Long2ObjectOpenHashMap[1<<numSlicesBits];
        this.secondaryCaches = new Long2ObjectLinkedOpenHashMap[1<<numSlicesBits];
        this.secondaryCacheLimits = new int[1<<numSlicesBits];
        this.locks = new StampedLock[1<<numSlicesBits];
        for (int i = 0; i < this.loadedSectionCache.length; i++) {
            this.loadedSectionCache[i] = new Long2ObjectOpenHashMap<>(1024);
            this.secondaryCaches[i] = new Long2ObjectLinkedOpenHashMap<>();
            this.secondaryCacheLimits[i] = cacheSize / this.loadedSectionCache.length
                    + (i < cacheSize % this.loadedSectionCache.length ? 1 : 0);
            this.locks[i] = new StampedLock();
        }
    }

    public WorldSection acquire(int lvl, int x, int y, int z, boolean nullOnEmpty) {
        return this.acquire(WorldEngine.getWorldSectionId(lvl, x, y, z), nullOnEmpty);
    }

    public WorldSection acquire(long key, boolean nullOnEmpty) {
        //TODO: add optional verification check to ensure this (or other critical systems) arnt being called on the render or server thread
        if (this.engine != null) this.engine.lastActiveTime = System.currentTimeMillis();
        int index = this.getCacheArrayIndex(key);
        var cache = this.loadedSectionCache[index];
        final var lock = this.locks[index];
        SectionHolder holder = null;
        boolean isLoader = false;
        WorldSection section = null;
        boolean reservedAcquire = false;

        {
            long stamp = lock.readLock();
            holder = cache.get(key);
            if (holder != null) {//Return already loaded entry
                section = holder.obj;
                if (section != null) {
                    boolean unavailable = nullOnEmpty && section.getStorageLoadStatus() != SectionStorage.LOAD_OK
                            && section.getStorageLoadStatus() != SectionStorage.LOAD_RECOVERED;
                    try {
                        section.acquire();
                    } finally {
                        // Even a violated live-holder invariant must not poison the slice lock.
                        lock.unlockRead(stamp);
                    }
                    if (unavailable) {
                        section.release();
                        return null;
                    }
                    return section;
                }
                reservedAcquire = holder.reservePendingAcquire();
                lock.unlockRead(stamp);
            } else {//Try to create holder
                holder = new SectionHolder();
                long ws = lock.tryConvertToWriteLock(stamp);
                if (ws == 0) {//Failed to convert, unlock read and get write
                    lock.unlockRead(stamp);
                    stamp = lock.writeLock();
                } else {
                    stamp = ws;
                }
                var eHolder = cache.putIfAbsent(key, holder);//We put if absent because on failure to convert to write, it leaves race condition
                lock.unlockWrite(stamp);
                if (eHolder == null) {//We are the loader
                    isLoader = true;
                } else {
                    holder = eHolder;
                    reservedAcquire = holder.reservePendingAcquire();
                }
            }
        }

        if (isLoader) {
            this.loadedSections.incrementAndGet();
            long stamp = lock.writeLock();
            try {
                section = this.secondaryCaches[index].remove(key);
                if (section != null) {
                    this.secondaryCachedSections.decrementAndGet();
                    this.secondaryCacheHits.increment();
                    // Keep reactivation and the loader's first reference under the same
                    // slice lock as removal; an older unload callback must not free the
                    // zero-reference gap before this holder has even been published.
                    section.primeForReuse();
                    section.acquire(1);
                } else {
                    this.secondaryCacheMisses.increment();
                }
            } finally {
                lock.unlockWrite(stamp);
            }
        }

        //If this thread was the one to create the reference then its the thread to load the section
        if (isLoader) {
            int status = section == null ? SectionStorage.LOAD_OK : section.getStorageLoadStatus();
            try {
                if (section == null) {//Secondary cache miss
                    section = new WorldSection(WorldEngine.getLevel(key),
                            WorldEngine.getX(key),
                            WorldEngine.getY(key),
                            WorldEngine.getZ(key),
                            this);

                    status = this.loader.load(section);

                    if (status < 0) {
                        Logger.error("Unable to load section " + section.key
                                + "; exposing temporary air while persisted data remains unavailable");
                        status = SectionStorage.LOAD_UNAVAILABLE;
                    }

                    if (status == SectionStorage.LOAD_MISSING || status == SectionStorage.LOAD_UNAVAILABLE) {
                        int sky = 15;
                        int block = 0;
                        Arrays.fill(section.data, Mapper.composeMappingId((byte) (sky|(block<<4)),0,0));
                    }
                    section._setStorageLoadStatus(status);
                    section.acquire(1);
                }
                holder.publish(section);
            } catch (Throwable throwable) {
                holder.fail(throwable);
                long stamp = lock.writeLock();
                try {
                    if (cache.get(key) == holder) {
                        cache.remove(key);
                        this.loadedSections.decrementAndGet();
                    }
                } finally {
                    lock.unlockWrite(stamp);
                }
                if (section != null && section.getRefCount() == 0 && section.trySetFreed()) {
                    section._releaseArray();
                }
                throw throwable;
            }
            if (nullOnEmpty && status != SectionStorage.LOAD_OK
                    && status != SectionStorage.LOAD_RECOVERED) {//If unavailable return null as stated, release the section aswell
                section.release();
                return null;
            }
            return section;
        } else {
            long waitStart = System.nanoTime();
            this.loaderWaitCount.increment();
            section = holder.await();
            this.loaderWaitNanos.add(System.nanoTime() - waitStart);
            if (reservedAcquire) {
                if (nullOnEmpty && section.getStorageLoadStatus() != SectionStorage.LOAD_OK
                        && section.getStorageLoadStatus() != SectionStorage.LOAD_RECOVERED) {
                    section.release();
                    return null;
                }
                return section;
            }
            if (section.tryAcquire()) {
                if (nullOnEmpty && section.getStorageLoadStatus() != SectionStorage.LOAD_OK
                        && section.getStorageLoadStatus() != SectionStorage.LOAD_RECOVERED) {
                    section.release();
                    return null;
                }
                return section;
            }
            return this.acquire(key, nullOnEmpty);
        }
    }

    public void tryUnload(WorldSection section, int hints) {
        if (this.engine != null) this.engine.lastActiveTime = System.currentTimeMillis();
        if (section.shouldSave()&&this.engine!=null) {
            if (section.tryAcquire()) {
                if (section.shouldSave()) {//If we should try enqueue
                    if (!this.engine.saveSection(section, true, true)) {
                        //we didnt enqueue the section in the save queue so we must unload it manually
                        section.release(false, hints);
                    }
                } else {
                    section.release(false, hints);//Special release
                }
            }
        }

        if (section.getRefCount() != 0) {
            return;
        }
        int index = this.getCacheArrayIndex(section.key);
        final var cache = this.loadedSectionCache[index];
        WorldSection sec = null;
        final var lock = this.locks[index];
        long stamp = lock.writeLock();
        //Any throw inside this region previously leaked the slice write lock, permanently
        // wedging every thread that touches this cache slice (including Embeddium builder
        // threads running shared Voxy jobs, which then freezes ChunkBuilder shutdown). Track
        // the unlock explicitly and back-stop it in finally.
        boolean stampReleased = false;
        boolean shouldRetryExit = false;
        WorldSection aa = null;
        try {
            SectionHolder currentHolder = cache.get(section.key);
            if (currentHolder == null || currentHolder.obj != section) {
                // Duplicate/delayed release callbacks belong to an older holder. They
                // cannot claim or remove a replacement that is still being published.
                return;
            }
            VarHandle.loadLoadFence();
            if (this.engine != null && section.shouldSave()) {//Last call for saving
                if (section.tryAcquire()) {
                    if (!this.engine.saveSection(section, true, true)) {//not allowed to block as we are in a lock
                        //We didnt enqueue the save here, so we must unload
                        // but unload in a recursive
                        VarHandle.fullFence();
                        shouldRetryExit |= section.getRefCount()!=1;//if we arnt the only ref
                        VarHandle.fullFence();
                        shouldRetryExit |= section.isDirty;//or if the section is now dirty, note this must go AFTER the ref check, since you can only mark live sections as dirty
                        section.release(false, hints);//Special
                    }


                    //NOTE: think have since fixed this issue
                    //In theory there can be a race condition here, where if this thread is paused
                    // the save queue fully finishes, the state is dirty == false inSaveQueue == false
                    // but the acquire count is at least 1
                    //if another thread marks this chunk as dirty (it would have acquired it after the inital `section.getRefCount() != 0`
                    // return check) and releases it, since the acquire count is still 1 (acquired here)
                    // then it doesnt trigger a save attempt but the dirty flag is set
                    //then this code continues and it causes badness cause its now in an invalid state
                } else {
                    throw new IllegalStateException("Section was dirty but is also unloaded, this is very bad");
                }
            }

            //This is a painful case, we need to abort here if there was a funky thing that happened
            if (shouldRetryExit) {
                lock.unlockWrite(stamp);
                stampReleased = true;
                //retry
                this.tryUnload(section, hints);
                return;
            }

            if (section.getRefCount() == 0 && this.engine != null && section.shouldSave()) {
                lock.unlockWrite(stamp);
                stampReleased = true;
                this.tryUnload(section, hints);
                return;
            }
            if (section.getRefCount() == 0 && section.inSaveQueue) {
                lock.unlockWrite(stamp);
                stampReleased = true;
                return;
            }
            if (section.getRefCount() == 0 && this.engine != null && section.isDirty) {
                //A racing dirty mark/save-queue transition can slip in between the save guards
                // above and the free below (markDirty and the save queue flags do not take the
                // slice lock); freeing now would trip the trySetFreed dirty invariant. Retry so
                // the save path picks the section up instead.
                lock.unlockWrite(stamp);
                stampReleased = true;
                this.tryUnload(section, hints);
                return;
            }

            if (section.getRefCount() == 0 && section.trySetFreed()) {
                var cached = cache.remove(section.key);
                var obj = cached.obj;
                if (obj == null) {
                    throw new IllegalStateException("This should be impossible: " + WorldEngine.pprintPos(section.key) + " secObj: " + System.identityHashCode(section));
                }
                if (obj != section) {
                    throw new IllegalStateException("Removed section not the same as the referenced section in the cache: cached: " + obj + " got: " + section + " A: " + WorldSection.ATOMIC_STATE_HANDLE.get(obj) + " B: " +WorldSection.ATOMIC_STATE_HANDLE.get(section));
                }
                sec = section;
            } else if (section.getRefCount() == 0 && this.engine != null && section.shouldSave()) {
                // trySetFreed may have cancelled a dirty claim after the final precheck.
                // Retry the existing save route outside the non-reentrant slice lock.
                lock.unlockWrite(stamp);
                stampReleased = true;
                this.tryUnload(section, hints);
                return;
            }

            if (sec != null) {
                Long2ObjectLinkedOpenHashMap<WorldSection> secondary = this.secondaryCaches[index];
                WorldSection previous = secondary.put(section.key, section);
                if (previous != null) {
                    throw new IllegalStateException("duplicate sections in cache is impossible");
                }
                this.secondaryCachedSections.incrementAndGet();
                if (this.secondaryCacheLimits[index] < secondary.size()) {
                    aa = secondary.removeFirst();
                    this.secondaryCachedSections.decrementAndGet();
                    this.secondaryCacheEvictions.increment();
                }
                lock.unlockWrite(stamp);
                stampReleased = true;
            } else {
                lock.unlockWrite(stamp);
                stampReleased = true;
            }
        } finally {
            if (!stampReleased) {
                lock.unlockWrite(stamp);
            }
        }


        if (aa != null) {
            aa._releaseArray();
        }

        if (sec != null) {
            this.loadedSections.decrementAndGet();
        }
    }

    private int getCacheArrayIndex(long pos) {
        return (int) (mixStafford13(pos) & (this.loadedSectionCache.length-1));
    }

    public static long mixStafford13(long seed) {
        seed = (seed ^ seed >>> 30) * -4658895280553007687L;
        seed = (seed ^ seed >>> 27) * -7723592293110705685L;
        return seed ^ seed >>> 31;
    }

    public int getLoadedCacheCount() {
        return this.loadedSections.get();
    }

    public int getSecondaryCacheSize() {
        return this.secondaryCachedSections.get();
    }

    long getLoaderWaitCount() {
        return this.loaderWaitCount.sum();
    }

    long getLoaderWaitNanos() {
        return this.loaderWaitNanos.sum();
    }

    long getSecondaryCacheHits() {
        return this.secondaryCacheHits.sum();
    }

    long getSecondaryCacheMisses() {
        return this.secondaryCacheMisses.sum();
    }

    long getSecondaryCacheEvictions() {
        return this.secondaryCacheEvictions.sum();
    }

    public static void main(String[] args) throws InterruptedException {
        var tracker = new ActiveSectionTracker(6, a->0, 2<<10);
        var bean = tracker.acquire(0, 0, 0, 9, false);
        var bean2 = tracker.acquire(1, 0, 0, 0, false);
        System.out.println("Target obj:" + System.identityHashCode(bean2));
        bean2.release();
        Thread[] ts = new Thread[10];
        for (int i = 0; i < ts.length;i++) {
            int tid = i;
            ts[i] = new Thread(()->{
                try {
                    for (int j = 0; j < 5000; j++) {
                        if (true) {
                            var section = tracker.acquire(0, 0, 0, 0, false);
                            section.acquire();
                            var section2 = tracker.acquire(1, 0, 0, 0, false);
                            section.release();
                            section.release();
                            section2.release();
                        }
                        if (true) {

                            var section = tracker.acquire(0, 0, 0, 0, false);
                            var section2 = tracker.acquire(1, 0, 0, 0, false);
                            section2.release();
                            section.release();
                        }
                        if (true) {
                            tracker.acquire(1, 0, 0, 0, false).release();
                        }
                    }
                } catch (Exception e) {
                    throw new RuntimeException("Thread " + tid, e);
                }
            });
            ts[i].start();
        }
        for (var t : ts) {
            t.join();
        }
    }
}
