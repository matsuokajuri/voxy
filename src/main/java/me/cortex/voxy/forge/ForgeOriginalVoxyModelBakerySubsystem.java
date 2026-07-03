package me.cortex.voxy.forge;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import me.cortex.voxy.common.Logger;
import me.cortex.voxy.common.world.other.Mapper;
import net.minecraft.client.Minecraft;

import java.util.concurrent.atomic.AtomicLong;
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
 * failures; upload draining is exposed with the Forge render-thread upload budget instead of
 * the ignored original nanosecond budget parameter.
 */
final class ForgeOriginalVoxyModelBakerySubsystem {
    private final ForgeOriginalVoxyModelStore storage = new ForgeOriginalVoxyModelStore();
    final ForgeOriginalVoxyModelFactory factory;
    private final Mapper mapper;

    private final Thread processingThread;
    private volatile boolean isRunning = true;
    private volatile Throwable processingThreadException;

    //Original ModelBakerySubsystem worker-side request dedupe
    private final ReentrantLock seenIdsLock = new ReentrantLock();
    private final ReentrantLock enqueueLock = new ReentrantLock();
    private final IntOpenHashSet seenIds = new IntOpenHashSet(6000);
    private final AtomicLong blockBakeRequestCount = new AtomicLong();

    ForgeOriginalVoxyModelBakerySubsystem(Mapper mapper, Minecraft minecraft) {
        this.mapper = mapper;
        String storeError = this.storage.build(minecraft);
        if (!"none".equals(storeError)) {
            this.storage.free();
            throw new IllegalStateException(storeError);
        }
        this.factory = new ForgeOriginalVoxyModelFactory(mapper, this.storage);
        this.factory.prepareOnRenderThread(minecraft);
        this.processingThread = new Thread(() -> {
            while (this.isRunning) {
                while (this.factory.processAllThings());
                LockSupport.park();
            }
        }, "Model factory processor");
        this.processingThread.setUncaughtExceptionHandler((thread, exception) -> {
            this.isRunning = false;
            this.processingThreadException = exception == null
                    ? new RuntimeException("unhandled-model-factory-exception")
                    : exception;
        });
        this.processingThread.start();
    }

    /**
     * Original {@code tick(long totalBudget)}: propagate worker death, then drain render-thread
     * uploads. Returns the processed upload count for the Forge stats layer.
     */
    int tick(int maxUploads) {
        if (this.processingThreadException != null) {
            throw new RuntimeException(this.processingThreadException);
        }
        return this.factory.processUploadsOnRenderThread(maxUploads);
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

        this.factory.shutdown();
        this.storage.free();
    }

    void requestBlockBake(int blockId) {
        if (this.mapper.getBlockStateCount() <= blockId) {
            Logger.error("Error, got bakeing request for out of range state id. StateId: " + blockId + " max id: " + this.mapper.getBlockStateCount(), new Exception());
            return;
        }
        this.seenIdsLock.lock();
        if (!this.seenIds.add(blockId)) {
            this.seenIdsLock.unlock();
            return;
        }
        this.seenIdsLock.unlock();
        this.blockBakeRequestCount.incrementAndGet();
        this.enqueueLock.lock();
        this.factory.addEntry(blockId);
        this.enqueueLock.unlock();
        LockSupport.unpark(this.processingThread);
    }

    void addBiome(Mapper.BiomeEntry biomeEntry) {
        if (biomeEntry == null) {
            return;
        }
        this.factory.addBiome(biomeEntry);
        LockSupport.unpark(this.processingThread);
    }

    ForgeOriginalVoxyModelStore getStore() {
        return this.storage;
    }

    boolean areQueuesEmpty() {
        return this.factory.areQueuesEmpty();
    }

    boolean hasPendingUploads() {
        return this.factory.hasPendingUploads();
    }

    Throwable processingThreadException() {
        return this.processingThreadException;
    }

    boolean workerReady() {
        return this.isRunning && this.processingThread.isAlive() && this.processingThreadException == null;
    }

    long blockBakeRequestCount() {
        return this.blockBakeRequestCount.get();
    }
}
