package me.cortex.voxy.forge;

import java.util.function.BiConsumer;

/** Completes the existing owner's ordered cleanup even when an earlier stage fails. */
final class RendererShutdownSequence {
    private final BiConsumer<String, Throwable> report;
    private IllegalStateException aggregate;

    RendererShutdownSequence(BiConsumer<String, Throwable> report) {
        this.report = report;
    }

    boolean attempt(String stage, Runnable action) {
        try {
            action.run();
            return true;
        } catch (RuntimeException | Error failure) {
            if (this.aggregate == null) {
                this.aggregate = new IllegalStateException("Renderer shutdown failed at " + stage, failure);
            } else {
                this.aggregate.addSuppressed(failure);
            }
            try {
                this.report.accept(stage, failure);
            } catch (RuntimeException | Error reportingFailure) {
                // A failing error reporter must not prevent the remaining resources being
                // released either. Its failure remains visible in the final aggregate.
                this.aggregate.addSuppressed(reportingFailure);
            }
            return false;
        }
    }

    void throwIfFailed() {
        if (this.aggregate != null) throw this.aggregate;
    }
}
