package me.cortex.voxy.common.thread;

import me.cortex.voxy.common.Logger;
import me.cortex.voxy.common.util.Pair;

import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public class Service {
    private final PerThreadContextExecutor executor;
    private final ServiceManager sm;
    final long weight;
    final String name;
    final BooleanSupplier limiter;

    private final Semaphore tasks = new Semaphore(0);
    private final ReentrantLock lifecycleLock = new ReentrantLock();
    private final Condition noRunningJobs = this.lifecycleLock.newCondition();
    private volatile boolean isLive = true;
    private volatile boolean isStopping = false;
    private int runningJobs;

    Service(Supplier<Pair<Runnable, Runnable>> ctxSupplier, ServiceManager sm, long weight, String name, BooleanSupplier limiter) {
        this.sm = sm;
        this.weight = weight;
        this.name = name;
        this.limiter = limiter;

        this.executor = new PerThreadContextExecutor(ctxSupplier, e->sm.handleException(this, e));
    }

    public void execute() {
        this.lifecycleLock.lock();
        try {
            if (this.isStopping || !this.isLive) {
                Logger.error("Tried executing on a dead service");
                return;
            }
            this.tasks.release();
            try {
                this.sm.execute(this);
            } catch (RuntimeException exception) {
                if (!this.tasks.tryAcquire()) {
                    exception.addSuppressed(new IllegalStateException("Unable to roll back failed service submission"));
                }
                throw exception;
            }
        } finally {
            this.lifecycleLock.unlock();
        }
    }

    boolean runJob() {
        this.lifecycleLock.lock();
        try {
            if (this.isStopping || !this.isLive || !this.tasks.tryAcquire()) {
                return false;
            }
            this.runningJobs++;
        } finally {
            this.lifecycleLock.unlock();
        }

        try {
            if (!this.executor.run()) {
                throw new IllegalStateException("Executor failed to run");
            }
            return true;
        } finally {
            this.lifecycleLock.lock();
            try {
                this.runningJobs--;
                if (this.runningJobs == 0) {
                    this.noRunningJobs.signalAll();
                }
            } finally {
                this.lifecycleLock.unlock();
            }
        }
    }

    public boolean isLive() {
        return this.isLive&&!this.isStopping;
    }

    public int numJobs() {
        return this.tasks.availablePermits();
    }

    public void blockTillEmpty() {
        while (this.isLive() && this.numJobs() != 0) {
            Thread.yield();
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public int shutdown() {
        int remaining;
        this.lifecycleLock.lock();
        try {
            if (this.isStopping || !this.isLive) {
                throw new IllegalStateException("Service not live");
            }
            this.isStopping = true;
            this.sm.removeService(this);
            remaining = this.tasks.drainPermits();
            this.sm.cancelJobs(remaining);
            while (this.runningJobs != 0) {
                this.noRunningJobs.awaitUninterruptibly();
            }
        } finally {
            this.lifecycleLock.unlock();
        }

        try {
            this.executor.shutdown();
        } finally {
            this.isLive = false;
        }
        return remaining;
    }

    public boolean steal() {
        this.lifecycleLock.lock();
        try {
            if (this.isStopping || !this.isLive || !this.tasks.tryAcquire()) {
                return false;
            }
            this.sm.cancelJobs(1);
            return true;
        } finally {
            this.lifecycleLock.unlock();
        }
    }

    public int drain() {
        this.lifecycleLock.lock();
        try {
            if (this.isStopping || !this.isLive) {
                return 0;
            }
            int drained = this.tasks.drainPermits();
            this.sm.cancelJobs(drained);
            return drained;
        } finally {
            this.lifecycleLock.unlock();
        }
    }
}
