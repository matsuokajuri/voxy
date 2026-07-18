package me.cortex.voxy.common.thread;

import me.cortex.voxy.common.util.TrackedObject;

import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntSupplier;

//Basiclly acts as a priority based mutlti semaphore
// allows the pooling of multiple threadpools together while prioritizing the work the original was ment for
public class MultiThreadPrioritySemaphore {
    public static final class Block extends TrackedObject {
        private final Semaphore blockSemaphore = new Semaphore(0);//The work pool semaphore
        private final Semaphore localSemaphore = new Semaphore(0);//The local semaphore
        private final AtomicInteger pooledSignals = new AtomicInteger();
        private final MultiThreadPrioritySemaphore man;

        Block(MultiThreadPrioritySemaphore man, int initialPooledSignals) {
            this.man = man;
            if (initialPooledSignals != 0) {
                this.pooledSignals.set(initialPooledSignals);
                this.blockSemaphore.release(initialPooledSignals);
            }
        }

        public void release(int permits) {
            requireNonNegativePermits(permits);
            //release local then block to prevent race conditions
            this.localSemaphore.release(permits);
            this.blockSemaphore.release(permits);
        }

        /*
        public void acquire() {
            this.acquire(true);
        }
        public void acquire(boolean runJob) {//Block until a permit for this block is availbe, other jobs maybe executed while we wait

            //while (true) {
            //    this.blockSemaphore.acquireUninterruptibly();//Block on all
            //    if (this.localSemaphore.tryAcquire()) {//We prioritize locals first
            //        return;
            //    }
            //    if (runJob) {
            //        //It wasnt a local job so run
            //        this.man.tryRun(this);
            //    } else {
            //        this.blockSemaphore.release(1);
            //        Thread.onSpinWait();
            //        Thread.yield();
            //    }
            //}

            //Absolutly no idea if this shitty thing functions correctly... at all, it very much probably doesnt
            while (true) {
                if (runJob) {
                    this.blockSemaphore.acquireUninterruptibly();//Block on all
                    if (this.localSemaphore.tryAcquire()) {//We prioritize locals first
                        return;
                    }
                    if (this.man.tryRun(this)) {//Returns true if it captured a local job
                        break;
                    }
                } else {
                    this.localSemaphore.acquireUninterruptibly();
                    if (!this.blockSemaphore.tryAcquire()) {
                        //This is technicanlly/actually a failure state cause blockSemaphore could have more
                    }
                    break;
                }
            }
        }*/


        public void acquire() {
            this.acquire(true);
        }
        public void acquire(boolean contributeToPool) {
            if (contributeToPool) {
                while (true) {
                    this.blockSemaphore.acquireUninterruptibly();//Block on all
                    if (this.localSemaphore.tryAcquire()) {//We prioritize locals first
                        return;
                    }
                    if (this.tryClaimPooledSignal() && this.man.tryRun(this)) {//Returns true if it captured a local job
                        break;
                    }
                }
            } else {
                this.localSemaphore.acquireUninterruptibly();//We acquire local first
                this.blockSemaphore.tryAcquire();//Try acquire a block, if not its... "fine"
            }
        }



        public void free() {
            this.man.freeBlock(this);
            this.free0();
        }

        public int availablePermits() {
            return this.localSemaphore.availablePermits();
        }

        int availablePooledSignals() {
            return this.pooledSignals.get();
        }

        int availableWakePermits() {
            return this.blockSemaphore.availablePermits();
        }

        public boolean tryAcquire() {
            if (this.localSemaphore.availablePermits()==0) return false;//Quick exit
            if (!this.blockSemaphore.tryAcquire()) return false;//There is definatly none
            if (this.localSemaphore.tryAcquire()) {
                //we acquired a proper permit
                return true;
            } else {
                //We must release the other permit as we dont do processing here
                this.blockSemaphore.release(1);
                return false;
            }
        }

        private boolean tryClaimPooledSignal() {
            return subtractAtMost(this.pooledSignals, 1) == 1;
        }

        private void pooledRelease(int permits) {
            this.pooledSignals.addAndGet(permits);
            this.blockSemaphore.release(permits);
        }

        private void pooledRetract(int permits) {
            int retracted = subtractAtMost(this.pooledSignals, permits);
            drainAtMost(this.blockSemaphore, retracted);
            int wakeFloor = this.localSemaphore.availablePermits() + this.pooledSignals.get();
            int wakePermits = this.blockSemaphore.availablePermits();
            if (wakePermits < wakeFloor) {
                this.blockSemaphore.release(wakeFloor - wakePermits);
            }
        }
    }

    private final Semaphore pooledSemaphore = new Semaphore(0);
    private final IntSupplier executor;

    private volatile Block[] blocks = new Block[0];

    public MultiThreadPrioritySemaphore(IntSupplier executor) {
        this.executor = executor;
    }

    public synchronized Block createBlock() {
        var block = new Block(this, this.pooledSemaphore.availablePermits());
        var blocks = Arrays.copyOf(this.blocks, this.blocks.length+1);
        blocks[blocks.length-1] = block;
        this.blocks = blocks;
        return block;
    }

    private synchronized void freeBlock(Block block) {
        var ob = this.blocks;
        var blocks = new Block[ob.length-1];
        int j = 0;
        for (int i = 0; i <= blocks.length; i++) {
            if (ob[i] != block) {
                blocks[j++] = ob[i];
            }
        }
        if (j != blocks.length) {
            throw new IllegalStateException("Could not find the service in the services array");
        }
        this.blocks = blocks;
    }

    public synchronized void pooledRelease(int permits) {
        requireNonNegativePermits(permits);
        this.pooledSemaphore.release(permits);
        for (var block : this.blocks) {
            block.pooledRelease(permits);
        }
    }

    public synchronized void pooledRetract(int permits) {
        requireNonNegativePermits(permits);
        drainAtMost(this.pooledSemaphore, permits);
        for (var block : this.blocks) {
            block.pooledRetract(permits);
        }
    }

    int availablePooledPermits() {
        return this.pooledSemaphore.availablePermits();
    }

    private boolean tryRun(Block block) {
        if (!this.pooledSemaphore.tryAcquire()) {//No jobs for the unified pool
            return false;
        }
        /*
        for (var otherBlock : this.blocks) {
            if (otherBlock != block) {
                block.debt.incrementAndGet();
            }
        }*/
        //Run the pooled job
        while (true) {
            int status = this.executor.getAsInt();
            if (status == 0) return false;//We finished pure and true
            if (status == 1) return false;// we didnt run a job because there either wasnt any or no services exist
            if (2 <= status) {//2 and 3 mean failed to find a service that can currently run, but should try again after a delay
                try {
                    if (block.localSemaphore.tryAcquire(10, TimeUnit.MILLISECONDS)) {//Await 10 millis for a local job to come in
                        //We do this confusing thing
                        block.blockSemaphore.tryAcquire();//Try acquire the block that we just got
                        this.pooledRelease(1);//We need to release back into the pool
                        return true;
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private static int subtractAtMost(AtomicInteger value, int requested) {
        while (true) {
            int current = value.get();
            int removed = Math.min(current, requested);
            if (removed == 0 || value.compareAndSet(current, current - removed)) {
                return removed;
            }
        }
    }

    private static int drainAtMost(Semaphore semaphore, int requested) {
        int removed = 0;
        while (removed < requested && semaphore.tryAcquire()) {
            removed++;
        }
        return removed;
    }

    private static void requireNonNegativePermits(int permits) {
        if (permits < 0) {
            throw new IllegalArgumentException("Permit count must be non-negative");
        }
    }
}
