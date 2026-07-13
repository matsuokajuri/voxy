package me.cortex.voxy.forge;

import java.util.ArrayList;

/** Direct Forge-side port of original Voxy's rolling CPU frame samplers. */
final class TimingStatistics {
    static double rollingWeight = 0.96D;
    private static final ArrayList<TimeSampler> ALL_SAMPLERS = new ArrayList<>();

    static final TimeSampler all = new TimeSampler();
    static final TimeSampler main = new TimeSampler();
    static final TimeSampler dynamic = new TimeSampler();
    static final TimeSampler postDynamic = new TimeSampler();
    static final TimeSampler A = new TimeSampler();
    static final TimeSampler B = new TimeSampler();
    static final TimeSampler C = new TimeSampler();
    static final TimeSampler D = new TimeSampler();
    static final TimeSampler E = new TimeSampler();
    static final TimeSampler F = new TimeSampler();
    static final TimeSampler G = new TimeSampler();
    static final TimeSampler H = new TimeSampler();
    static final TimeSampler I = new TimeSampler();

    private TimingStatistics() {
    }

    static void resetSamplers() {
        ALL_SAMPLERS.forEach(TimeSampler::reset);
    }

    static void update() {
        ALL_SAMPLERS.forEach(TimeSampler::update);
    }

    static final class TimeSampler {
        private boolean running;
        private long timestamp;
        private long runtime;
        private double rolling;

        private TimeSampler() {
            ALL_SAMPLERS.add(this);
        }

        private void reset() {
            if (this.running) {
                throw new IllegalStateException("Voxy timing sampler was still running");
            }
            this.runtime = 0L;
        }

        void start() {
            if (this.running) {
                throw new IllegalStateException("Voxy timing sampler started twice");
            }
            this.running = true;
            this.timestamp = System.nanoTime();
        }

        void stop() {
            if (!this.running) {
                throw new IllegalStateException("Voxy timing sampler stopped while idle");
            }
            this.running = false;
            this.runtime += System.nanoTime() - this.timestamp;
        }

        void stopIfRunning() {
            if (this.running) {
                this.stop();
            }
        }

        @SuppressWarnings("unused")
        void subtract(TimeSampler sampler) {
            this.runtime -= sampler.runtime;
        }

        private void update() {
            double time = (double) (this.runtime / 1_000L) / 1_000D;
            this.rolling = Math.max(
                    this.rolling * rollingWeight + time * (1.0D - rollingWeight),
                    time);
        }

        String pVal() {
            return String.format(java.util.Locale.ROOT, "%6.3f", this.rolling);
        }
    }
}
