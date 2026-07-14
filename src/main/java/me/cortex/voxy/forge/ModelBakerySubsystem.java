package me.cortex.voxy.forge;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import me.cortex.voxy.common.Logger;
import me.cortex.voxy.common.world.other.Mapper;
import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Port of original {@code me.cortex.voxy.client.core.model.ModelBakerySubsystem} for the Forge
 * parity route. Owns the model store + model factory pair and the "Model factory processor"
 * worker thread; everything is created and the worker is started inside this constructor so
 * model-bakery setup sits inside the original {@code VoxyRenderSystem} constructor boundary.
 *
 * Forge adaptations: the store/factory GL setup requires the Minecraft instance and reports
 * error strings instead of throwing, so store build failures are rethrown here as construction
 * failures; tick returns a processed count for the Forge stats layer while preserving the
 * original drain-all upload contract and its ignored nanosecond budget parameter.
 */
final class ModelBakerySubsystem {
    private final ModelStore storage = new ModelStore();
    final ModelFactory factory;
    private final Mapper mapper;

    private final Thread processingThread;
    private volatile boolean isRunning = true;
    private volatile Throwable processingThreadException;

    //Original ModelBakerySubsystem worker-side request dedupe
    private final ReentrantLock seenIdsLock = new ReentrantLock();
    private final ReentrantLock enqueueLock = new ReentrantLock();
    private final IntOpenHashSet seenIds = new IntOpenHashSet(6000);

    ModelBakerySubsystem(Mapper mapper, Minecraft minecraft) {
        this.mapper = mapper;
        String storeError = this.storage.build(minecraft);
        if (!"none".equals(storeError)) {
            this.storage.free();
            throw new IllegalStateException(storeError);
        }
        this.factory = new ModelFactory(mapper, this.storage);
        this.factory.prepareOnRenderThread(minecraft);
        this.processingThread = new Thread(() -> {
            while (this.isRunning) {
                //Mirror original ModelFactory.processAllThings(): only worker-owned bake/biome
                //work keeps this inner loop running. The render-thread upload queue is tracked
                //separately by areQueuesEmpty(), and the shutdown guard prevents a join deadlock.
                while (this.isRunning && this.factory.processAllThings());
                if (this.isRunning) {
                    LockSupport.park();
                }
            }
        }, "Model factory processor");
        this.processingThread.setUncaughtExceptionHandler((thread, exception) -> this.recordFactoryFailure(exception));
        this.processingThread.start();
    }

    /**
     * Original {@code tick(long totalBudget)}: propagate worker death, then drain render-thread
     * uploads.
     */
    void tick() {
        if (this.processingThreadException != null) {
            throw new RuntimeException(this.processingThreadException);
        }
        this.factory.processUploads();
    }

    void shutdown() {
        this.isRunning = false;
        LockSupport.unpark(this.processingThread);
        try {
            this.processingThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }

        this.factory.free();
        this.storage.free();
    }

    void requestBlockBake(int blockId) {
        if (this.mapper.getBlockStateCount() <= blockId) {
            Logger.error("Error, got bakeing request for out of range state id. StateId: " + blockId + " max id: " + this.mapper.getBlockStateCount(), new Exception());
            return;
        }
        boolean enqueued;
        this.seenIdsLock.lock();
        try {
            if (this.seenIds.contains(blockId)) {
                return;
            }
            this.enqueueLock.lock();
            try {
                try {
                    enqueued = this.factory.addEntry(blockId);
                } catch (RuntimeException e) {
                    //Requests run on section-meshing workers. Publish the failure for the formal
                    // owner tick instead of letting the shared service executor log-and-swallow it.
                    this.recordFactoryFailure(e);
                    return;
                }
            } finally {
                this.enqueueLock.unlock();
            }
            // A normal false result is still a completed dedupe decision: the mapping already
            // exists or the same id is already in flight. ModelFactory reports real failures by
            // throwing, so only the exceptional path above must leave the id unseen for retry.
            this.seenIds.add(blockId);
        } finally {
            this.seenIdsLock.unlock();
        }
        if (enqueued) {
            LockSupport.unpark(this.processingThread);
        }
    }

    void addBiome(Mapper.BiomeEntry biomeEntry) {
        if (biomeEntry == null) {
            return;
        }
        this.factory.addBiome(biomeEntry);
        LockSupport.unpark(this.processingThread);
    }

    ModelStore getStore() {
        return this.storage;
    }

    boolean areQueuesEmpty() {
        return this.factory.areQueuesEmpty();
    }

    boolean hasPendingUploads() {
        return this.processingThreadException != null || this.factory.hasPendingUploads();
    }

    void addDebugData(List<String> debug) {
        debug.add(String.format("IF/MC: %03d, %04d", this.factory.getInflightCount(), this.factory.getBakedCount()));
    }

    private synchronized void recordFactoryFailure(Throwable exception) {
        this.isRunning = false;
        if (this.processingThreadException == null) {
            this.processingThreadException = exception == null
                    ? new RuntimeException("unhandled-model-factory-exception")
                    : exception;
        }
        LockSupport.unpark(this.processingThread);
    }
}
