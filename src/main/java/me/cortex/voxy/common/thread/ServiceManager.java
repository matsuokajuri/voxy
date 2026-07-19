package me.cortex.voxy.common.thread;

import it.unimi.dsi.fastutil.HashCommon;
import me.cortex.voxy.common.Logger;
import me.cortex.voxy.common.util.Pair;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

public class ServiceManager {
    private static final int MAX_SERVICES = Long.SIZE;
    private static final long MAX_SERVICE_WEIGHT = Long.MAX_VALUE / Integer.MAX_VALUE;

    private static final class ThreadCtx {
        int shiftFactor = 0;
        long seed;//Random seed used for selecting service

        ThreadCtx() {
            this.seed = HashCommon.murmurHash3(System.nanoTime()^System.identityHashCode(this));
        }

        long rand(long size) {
            return Long.remainderUnsigned(this.seed = HashCommon.mix(this.seed), size);
        }
    }

    private final IntConsumer jobRelease;
    private final IntConsumer jobRetract;
    private final Object jobMonitor = new Object();
    private final ThreadLocal<ThreadCtx> accelerationContext = ThreadLocal.withInitial(ThreadCtx::new);
    private final AtomicInteger totalJobs = new AtomicInteger();
    private volatile Service[] services = new Service[0];
    private volatile boolean isShutdown = false;

    public ServiceManager(IntConsumer jobRelease, IntConsumer jobRetract) {
        this.jobRelease = jobRelease;
        this.jobRetract = jobRetract;
    }


    public Service createServiceNoCleanup(Supplier<Runnable> ctxFactory, long weight) {
        return this.createService(()->new Pair<>(ctxFactory.get(), ()->{}), weight, "");
    }

    public Service createServiceNoCleanup(Supplier<Runnable> ctxFactory, long weight, String name) {
        return this.createService(()->new Pair<>(ctxFactory.get(), ()->{}), weight, name);
    }

    public Service createService(Supplier<Pair<Runnable, Runnable>> ctxFactory, long weight) {
        return this.createService(ctxFactory, weight, "");
    }

    public Service createService(Supplier<Pair<Runnable, Runnable>> ctxFactory, long weight, String name) {
        return this.createService(ctxFactory, weight, name, null);
    }
    public synchronized Service createService(Supplier<Pair<Runnable, Runnable>> ctxFactory, long weight, String name, BooleanSupplier limiter) {
        if (this.isShutdown) {
            throw new IllegalStateException("Service manager is shutdown");
        }
        if (weight <= 0 || weight > MAX_SERVICE_WEIGHT) {
            throw new IllegalArgumentException("Service weight is outside the safe positive range: " + weight);
        }
        if (this.services.length == MAX_SERVICES) {
            throw new IllegalStateException("Service manager supports at most " + MAX_SERVICES + " live services");
        }
        Service newService = new Service(ctxFactory, this, weight, name, limiter);
        var newServices = Arrays.copyOf(this.services, this.services.length+1);
        newServices[newServices.length-1] = newService;
        this.services = newServices;
        return newService;
    }

    public int tryRunAJob() {//Executes a single job on the current thread
        if (this.services.length == 0 || this.totalJobs.get() == 0) return 1;
        return this.runAJob0();
    }

    private int runAJob0() {//Executes a single job on the current thread
        if (this.services.length == 0) return 1;
        var ctx = this.accelerationContext.get();
        outer:
        while (true) {
            long skipMsk = 0;
            var services = this.services;//Capture the current services array
            if (services.length == 0) return 1;
            if (this.totalJobs.get()==0) return 1;
            long totalWeight = 0;
            int shiftFactor = (ctx.shiftFactor++)&Integer.MAX_VALUE;//We cycle and shift the starting service when choosing to prevent bias
            int c = shiftFactor;
            Service selectedService = null;
            for (int i = 0; i < services.length; i++) {
                var service = services[i];
                if (!service.isLive()) {
                    Thread.yield();
                    continue outer;//We need to refetch the array and start over
                }
                boolean sc = c--<=0;
                if (service.limiter!=null && !service.limiter.getAsBoolean()) {
                    skipMsk |= 1L<<i;
                    continue;
                }
                long jc = service.numJobs();
                if (sc&&jc!=0&&selectedService==null) selectedService=service;
                totalWeight += jc * service.weight;
            }
            if (totalWeight == 0) return skipMsk!=0?3:2;

            long sample = ctx.rand(totalWeight);//Random number

            for (int i = 0; i < services.length; i++) {
                int serviceIndex = (i+shiftFactor)%services.length;
                var service = services[serviceIndex];
                if (service.limiter!=null && (((skipMsk&(1L<<serviceIndex))!=0)|| !service.limiter.getAsBoolean())) {
                    skipMsk |= 1L<<serviceIndex;
                    continue;
                }
                sample -= service.numJobs() * service.weight;
                if (sample<0) {
                    selectedService = service;
                    break;
                }
            }

            if (selectedService == null) {
                return skipMsk!=0?3:2;
            }

            if (!selectedService.isLive()) {
                continue;//Failed to select a live service, try again
            }

            try {
                if (!selectedService.runJob()) {
                    //We failed to run the service, try again
                    continue;
                }
            } catch (RuntimeException | Error throwable) {
                this.completeJob();
                throw throwable;
            }
            this.completeJob();
            break;
        }
        return 0;
    }

    public void shutdown() {
        synchronized (this) {
            if (this.isShutdown) {
                throw new IllegalStateException("Service manager already shutdown");
            }
            if (this.services.length != 0) {
                for (var s : this.services) {
                    if (s.isLive()) {
                        throw new IllegalStateException("Service '" + s.name + "' was not in shutdown when manager shutdown");
                    }
                }
                throw new IllegalStateException("Service manager still contains stopped services");
            }
            this.isShutdown = true;
        }

        synchronized (this.jobMonitor) {
            boolean interrupted = false;
            while (this.totalJobs.get() != 0) {
                try {
                    this.jobMonitor.wait();
                } catch (InterruptedException exception) {
                    interrupted = true;
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    synchronized void removeService(Service service) {
        var services = this.services;
        var newServices = new Service[services.length-1];
        int j = 0;
        for (int i = 0; i < services.length; i++) {
            if (services[i] != service) {
                newServices[j++] = services[i];
            }
        }
        if (j != newServices.length) {
            throw new IllegalStateException("Could not find the service in the services array");
        }

        this.services = newServices;
    }

    void execute(Service service) {
        if (this.isShutdown) {
            throw new IllegalStateException("Cannot submit to a shutdown service manager");
        }
        this.totalJobs.incrementAndGet();
        try {
            this.jobRelease.accept(1);
        } catch (RuntimeException exception) {
            this.completeJob();
            throw exception;
        }
    }

    void cancelJobs(int cancelled) {
        if (cancelled == 0) {
            return;
        }
        if (cancelled < 0) {
            throw new IllegalArgumentException("Cancelled job count must be non-negative");
        }
        this.subtractJobs(cancelled, "Cancelled more jobs than were submitted");
        this.jobRetract.accept(cancelled);
    }

    int totalJobCount() {
        return this.totalJobs.get();
    }

    boolean isShutdown() {
        return this.isShutdown;
    }

    private void completeJob() {
        this.subtractJobs(1, "Completed a job when no submitted jobs remained");
    }

    private void subtractJobs(int count, String failureMessage) {
        while (true) {
            int current = this.totalJobs.get();
            if (current < count) {
                throw new IllegalStateException(failureMessage + ": current=" + current + ", requested=" + count);
            }
            if (this.totalJobs.compareAndSet(current, current - count)) {
                if (current == count) {
                    synchronized (this.jobMonitor) {
                        this.jobMonitor.notifyAll();
                    }
                }
                return;
            }
        }
    }

    void handleException(Service service, Exception exception) {
        Logger.error("Service '"+service.name+"' on thread '"+Thread.currentThread().getName()+"' had an exception", exception);
    }
}
