package me.cortex.voxy.forge;

import com.mojang.blaze3d.systems.RenderSystem;

import java.util.Collections;
import java.util.List;

final class ForgeDirectGpuGeometryDrawPlanner {
    private ForgeDirectGpuGeometryDrawPlanner() {
    }

    static PlanResult plan(ForgeVoxyInstance instance, int maxSections, int maxRecords) {
        long start = System.nanoTime();
        if (!RenderSystem.isOnRenderThread()) {
            return failure(start, "NOT_RENDER_THREAD", "not-render-thread", 0, 0);
        }
        if (!ForgeDirectGpuGeometryRendererConfig.isEnabled()) {
            return failure(start, "DISABLED", "direct-renderer-disabled", 0, 0);
        }

        ForgeGpuGeometryUploadManager uploadManager = instance.getGpuGeometryUploadManager();
        ForgeGpuGeometryHeap heap = uploadManager.getHeapForDebugReadback();
        if (heap == null) {
            return failure(start, "HEAP_MISSING", "heap-reference-missing", 0, 0);
        }
        if (!heap.isCreated()) {
            return failure(start, "HEAP_MISSING", "heap-not-created", 0, 0);
        }

        List<Integer> sectionIds = uploadManager.createUploadedSectionIdSnapshot();
        Collections.sort(sectionIds);
        if (sectionIds.isEmpty()) {
            return failure(start, "NO_UPLOADED_SECTIONS", "no-uploaded-sections", 0, 0);
        }

        int sectionLimit = Math.max(1, maxSections);
        int recordLimit = Math.max(1, maxRecords);
        int plannedSections = 0;
        long plannedRecords = 0;
        long plannedVertices = 0;
        int skippedSections = 0;
        long skippedRecords = 0;
        int invalidMetadata = 0;
        String lastError = "none";
        String selectionMode = "SECTION_ID_ORDER";

        for (Integer sectionId : sectionIds) {
            if (sectionId == null || plannedSections >= sectionLimit || plannedRecords >= recordLimit) {
                if (sectionId != null) {
                    skippedSections++;
                }
                break;
            }
            try {
                int[] words = heap.readbackMetadata(sectionId);
                ForgeGpuGeometryDecodedMetadata metadata = ForgeGpuGeometryDecodedMetadata.decode(words);
                String validation = metadata.validate(heap, instance.getSectionGeometryManager(), sectionId, words);
                if (!"none".equals(validation)) {
                    invalidMetadata++;
                    lastError = "sectionId=" + sectionId + " " + validation;
                    continue;
                }

                int remainingRecords = recordLimit - (int) Math.min(Integer.MAX_VALUE, plannedRecords);
                int recordsForSection = Math.min(metadata.itemCount(), Math.max(0, remainingRecords));
                if (metadata.itemCount() > recordsForSection) {
                    skippedRecords += metadata.itemCount() - recordsForSection;
                }
                plannedRecords += recordsForSection;
                plannedVertices += (long) recordsForSection * 6L;
                plannedSections++;
            } catch (RuntimeException e) {
                invalidMetadata++;
                lastError = "sectionId=" + sectionId + " " + e.getClass().getSimpleName() + ": " + e.getMessage();
            }
        }

        double durationMs = elapsedMs(start);
        boolean success = plannedSections > 0;
        String skippedReason = plannedSections == 0 ? "NO_VALID_METADATA" : "none";
        String error = success ? "none" : lastError;
        return new PlanResult(
                success,
                skippedReason,
                error,
                sectionIds.size(),
                plannedSections,
                plannedRecords,
                plannedVertices,
                skippedSections,
                skippedRecords,
                selectionMode,
                invalidMetadata,
                durationMs
        );
    }

    private static PlanResult failure(long start, String skippedReason, String error, int candidates, int invalidMetadata) {
        return new PlanResult(
                false,
                skippedReason,
                error,
                candidates,
                0,
                0,
                0,
                0,
                0,
                skippedReason,
                invalidMetadata,
                elapsedMs(start)
        );
    }

    private static double elapsedMs(long start) {
        return (System.nanoTime() - start) / 1_000_000.0D;
    }

    record PlanResult(
            boolean success,
            String skippedReason,
            String error,
            int uploadedSectionCandidates,
            int plannedSections,
            long plannedRecords,
            long plannedVertices,
            int skippedSections,
            long skippedRecords,
            String selectionMode,
            int invalidMetadata,
            double durationMs
    ) {
    }
}
