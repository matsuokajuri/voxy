package me.cortex.voxy.common.thread;

import me.cortex.voxy.client.compat.SemaphoreBlockImpersonator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceCancellationAccountingTest {
    @Test
    @Timeout(20)
    void stealAndDrainRetractGlobalAndPerBlockWakeCredits() {
        Harness harness = new Harness();
        Service service = harness.manager.createServiceNoCleanup(() -> () -> {
        }, 1, "cancellation-accounting");
        try {
            for (int index = 0; index < 8; index++) {
                service.execute();
            }
            assertEquals(8, service.numJobs());
            assertEquals(8, harness.manager.totalJobCount());
            assertEquals(8, harness.signals.availablePooledPermits());
            assertEquals(8, harness.block.availablePooledSignals());
            assertEquals(8, harness.block.availableWakePermits());

            assertTrue(service.steal());
            assertEquals(7, harness.manager.totalJobCount());
            assertEquals(7, harness.signals.availablePooledPermits());
            assertEquals(7, harness.block.availablePooledSignals());
            assertEquals(7, harness.block.availableWakePermits());

            assertEquals(7, service.drain());
            assertAccountingEmpty(harness);
            assertEquals(0, service.shutdown());
            harness.manager.shutdown();
        } finally {
            harness.block.free();
        }
    }

    @Test
    @Timeout(20)
    void borrowedBlockRunsAJobWithoutLeavingCredits() {
        Harness harness = new Harness();
        AtomicInteger runs = new AtomicInteger();
        Service service = harness.manager.createServiceNoCleanup(() -> () -> {
            runs.incrementAndGet();
            harness.block.release(1);
        }, 1, "borrowed-worker");
        try {
            service.execute();
            harness.block.acquire();
            assertEquals(1, runs.get());
            assertAccountingEmpty(harness);
            service.shutdown();
            harness.manager.shutdown();
        } finally {
            harness.block.free();
        }
    }

    @Test
    @Timeout(20)
    void executeAndShutdownAreLinearizedBeforeSignalRetraction() throws Exception {
        AtomicReference<ServiceManager> managerReference = new AtomicReference<>();
        MultiThreadPrioritySemaphore signals = new MultiThreadPrioritySemaphore(
                () -> managerReference.get().tryRunAJob());
        CountDownLatch releaseEntered = new CountDownLatch(1);
        CountDownLatch allowRelease = new CountDownLatch(1);
        ServiceManager manager = new ServiceManager(permits -> {
            releaseEntered.countDown();
            await(allowRelease);
            signals.pooledRelease(permits);
        }, signals::pooledRetract);
        managerReference.set(manager);
        MultiThreadPrioritySemaphore.Block block = signals.createBlock();
        Service service = manager.createServiceNoCleanup(() -> () -> {
        }, 1, "execute-shutdown-race");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> execute = executor.submit(service::execute);
            assertTrue(releaseEntered.await(5, TimeUnit.SECONDS));
            Future<Integer> shutdown = executor.submit(service::shutdown);
            assertThrows(TimeoutException.class, () -> shutdown.get(150, TimeUnit.MILLISECONDS));

            allowRelease.countDown();
            execute.get(5, TimeUnit.SECONDS);
            assertEquals(1, shutdown.get(5, TimeUnit.SECONDS));
            assertEquals(0, service.numJobs());
            assertEquals(0, manager.totalJobCount());
            assertEquals(0, signals.availablePooledPermits());
            assertEquals(0, block.availablePooledSignals());
            assertEquals(0, block.availableWakePermits());
            manager.shutdown();
        } finally {
            allowRelease.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
            block.free();
        }
    }

    @Test
    @Timeout(20)
    void shutdownWaitsForClaimedJobBeforeCleaningExecutor() throws Exception {
        Harness harness = new Harness();
        CountDownLatch running = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        Service service = harness.manager.createServiceNoCleanup(() -> () -> {
            running.countDown();
            await(finish);
            harness.block.release(1);
        }, 1, "claimed-job-shutdown");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            service.execute();
            Future<?> worker = executor.submit(() -> harness.block.acquire());
            assertTrue(running.await(5, TimeUnit.SECONDS));
            Future<Integer> shutdown = executor.submit(service::shutdown);
            assertThrows(TimeoutException.class, () -> shutdown.get(150, TimeUnit.MILLISECONDS));

            finish.countDown();
            worker.get(5, TimeUnit.SECONDS);
            assertEquals(0, shutdown.get(5, TimeUnit.SECONDS));
            harness.manager.shutdown();
            assertAccountingEmpty(harness);
        } finally {
            finish.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
            harness.block.free();
        }
    }

    @Test
    @Timeout(20)
    void embeddiumSemaphoreBorrowReturnsLimitedJobCreditToPool() throws Exception {
        Harness harness = new Harness();
        AtomicBoolean allowVoxyJob = new AtomicBoolean();
        CountDownLatch limiterObserved = new CountDownLatch(1);
        AtomicInteger runs = new AtomicInteger();
        SemaphoreBlockImpersonator semaphore = new SemaphoreBlockImpersonator(harness.block);
        Service service = harness.manager.createService(
                () -> new me.cortex.voxy.common.util.Pair<>(() -> {
                    runs.incrementAndGet();
                    semaphore.release(1);
                }, () -> {
                }),
                1,
                "embeddium-borrow",
                () -> {
                    limiterObserved.countDown();
                    return allowVoxyJob.get();
                });
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            service.execute();
            Future<?> embeddiumWait = executor.submit(() -> {
                semaphore.acquire();
                return null;
            });
            assertTrue(limiterObserved.await(5, TimeUnit.SECONDS));
            semaphore.release(1);
            embeddiumWait.get(5, TimeUnit.SECONDS);

            assertEquals(0, runs.get());
            assertEquals(1, service.numJobs());
            assertEquals(1, harness.manager.totalJobCount());
            assertEquals(1, harness.signals.availablePooledPermits());
            assertEquals(1, harness.block.availablePooledSignals());

            allowVoxyJob.set(true);
            Future<?> borrowedRun = executor.submit(() -> {
                semaphore.acquire();
                return null;
            });
            borrowedRun.get(5, TimeUnit.SECONDS);
            assertEquals(1, runs.get());
            assertAccountingEmpty(harness);
            service.shutdown();
            harness.manager.shutdown();
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
            harness.block.free();
        }
    }

    @Test
    @Timeout(20)
    void cancellingLimitedJobRetiresInFlightBorrowSignalWithoutSpinning() throws Exception {
        Harness harness = new Harness();
        CountDownLatch limiterObserved = new CountDownLatch(1);
        Service service = harness.manager.createService(
                () -> new me.cortex.voxy.common.util.Pair<>(() -> {
                }, () -> {
                }),
                1,
                "limited-cancellation",
                () -> {
                    limiterObserved.countDown();
                    return false;
                });
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            service.execute();
            Future<?> borrowedWorker = executor.submit(() -> harness.block.acquire());
            assertTrue(limiterObserved.await(5, TimeUnit.SECONDS));

            assertEquals(1, service.shutdown());
            assertAccountingEmpty(harness);
            harness.block.release(1);
            borrowedWorker.get(5, TimeUnit.SECONDS);
            harness.manager.shutdown();
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
            harness.block.free();
        }
    }

    @Test
    @Timeout(20)
    void contextFactoryFailureDoesNotLeakRunningOrGlobalJobCounts() throws Exception {
        Harness harness = new Harness();
        AtomicInteger factoryAttempts = new AtomicInteger();
        Service service = harness.manager.createServiceNoCleanup(() -> {
            factoryAttempts.incrementAndGet();
            throw new IllegalStateException("expected-context-factory-failure");
        }, 1, "context-factory-failure");
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            service.execute();
            Future<?> borrowedWorker = executor.submit(() -> harness.block.acquire());
            awaitCondition(() -> harness.manager.totalJobCount() == 0);
            harness.block.release(1);
            borrowedWorker.get(5, TimeUnit.SECONDS);

            assertEquals(1, factoryAttempts.get());
            assertAccountingEmpty(harness);
            service.shutdown();
            harness.manager.shutdown();
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
            harness.block.free();
        }
    }

    @Test
    @Timeout(20)
    void prematureManagerShutdownDoesNotPoisonLaterOrderlyShutdown() {
        Harness harness = new Harness();
        Service service = harness.manager.createServiceNoCleanup(() -> () -> {
        }, 1, "manager-shutdown-order");
        try {
            assertThrows(IllegalStateException.class, harness.manager::shutdown);
            assertFalse(harness.manager.isShutdown());

            service.execute();
            assertTrue(service.steal());
            service.shutdown();
            harness.manager.shutdown();
            assertTrue(harness.manager.isShutdown());
            assertThrows(
                    IllegalStateException.class,
                    () -> harness.manager.createServiceNoCleanup(() -> () -> {
                    }, 1));
            assertAccountingEmpty(harness);
        } finally {
            harness.block.free();
        }
    }

    @Test
    @Timeout(30)
    void repeatedThreadCountChangesAndPoolShutdownLeaveNoWorkers() throws Exception {
        for (int cycle = 0; cycle < 3; cycle++) {
            UnifiedServiceThreadPool pool = new UnifiedServiceThreadPool();
            int taskCount = 64;
            CountDownLatch completed = new CountDownLatch(taskCount);
            Service service = pool.serviceManager.createServiceNoCleanup(
                    () -> completed::countDown,
                    1,
                    "thread-count-cycle-" + cycle);
            assertTrue(pool.setNumThreads(4));
            for (int task = 0; task < taskCount; task++) {
                service.execute();
            }
            assertTrue(completed.await(10, TimeUnit.SECONDS));
            service.blockTillEmpty();
            assertTrue(pool.setNumThreads(1));
            assertTrue(pool.setNumThreads(0));
            assertTrue(pool.setNumThreads(3));
            assertTrue(pool.setNumThreads(0));
            service.shutdown();
            pool.shutdown();
            assertEquals(0, pool.getNumThreads());
            assertThrows(IllegalStateException.class, () -> pool.setNumThreads(1));
        }
    }

    private static void assertAccountingEmpty(Harness harness) {
        assertEquals(0, harness.manager.totalJobCount());
        assertEquals(0, harness.signals.availablePooledPermits());
        assertEquals(0, harness.block.availablePooledSignals());
        assertEquals(0, harness.block.availableWakePermits());
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for test latch");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(exception);
        }
    }

    private static void awaitCondition(java.util.function.BooleanSupplier condition) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() >= deadline) {
                throw new IllegalStateException("Timed out waiting for test condition");
            }
            Thread.onSpinWait();
        }
    }

    private static final class Harness {
        final AtomicReference<ServiceManager> managerReference = new AtomicReference<>();
        final MultiThreadPrioritySemaphore signals = new MultiThreadPrioritySemaphore(
                () -> this.managerReference.get().tryRunAJob());
        final ServiceManager manager = new ServiceManager(this.signals::pooledRelease, this.signals::pooledRetract);
        final MultiThreadPrioritySemaphore.Block block = this.signals.createBlock();

        private Harness() {
            this.managerReference.set(this.manager);
        }
    }
}
