package me.cortex.voxy.forge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class ForgeMdicCommandList {
    private static final ForgeMdicCommandList EMPTY = new ForgeMdicCommandList(Collections.emptyList(), -1L, "none", 0L, 0, "none", 0, 0, 0, 0, false, false, false, false, false, false, "none", 0, 0, 0, 0, 0, 0, new int[ForgeGpuGeometryDecodedMetadata.BUCKET_COUNT], 0, 0, 0);

    private final List<ForgeMdicCommand> commands;
    private final long heapGeneration;
    private final String dimensionId;
    private final long buildTimeMillis;
    private final long recordCount;
    private final String selectionMode;
    private final int skippedSections;
    private final long skippedRecords;
    private final int planCandidateSections;
    private final int planAcceptedSections;
    private final boolean bucketAware;
    private final boolean includedTranslucent;
    private final boolean includedDoubleSided;
    private final boolean includedDirectional;
    private final boolean directionalFaceMask;
    private final boolean faceMaskFallbackAllWhenInside;
    private final String faceMaskFallbackReason;
    private final int faceMaskCommandsAccepted;
    private final int faceMaskCommandsRejected;
    private final int rejectedDirectionalBuckets;
    private final int insideSectionFallbacks;
    private final int missingCameraFallbacks;
    private final int missingAabbFallbacks;
    private final int[] bucketRejectedByFaceMask;
    private final int minRecordCount;
    private final int maxRecordCount;
    private final double avgRecordCount;
    private final int nonEmptyBucketCommands;
    private final int emptyBucketCommands;
    private final int bucketMaskOr;
    private final int bucketMaskAnd;
    private final int minGeometryPtr;
    private final int maxGeometryPtr;
    private final int sectionCount;
    private final int bucketCommands;
    private final int sectionCommands;
    private final int commandsPerSectionMin;
    private final int commandsPerSectionMax;
    private final int[] bucketCommandCounts;
    private final int skippedTranslucentCommands;
    private final int skippedEmptyBuckets;
    private final int skippedBucketCommands;

    private ForgeMdicCommandList(
            List<ForgeMdicCommand> commands,
            long heapGeneration,
            String dimensionId,
            long buildTimeMillis,
            long recordCount,
            String selectionMode,
            int skippedSections,
            long skippedRecords,
            int planCandidateSections,
            int planAcceptedSections,
            boolean bucketAware,
            boolean includedTranslucent,
            boolean includedDoubleSided,
            boolean includedDirectional,
            boolean directionalFaceMask,
            boolean faceMaskFallbackAllWhenInside,
            String faceMaskFallbackReason,
            int faceMaskCommandsAccepted,
            int faceMaskCommandsRejected,
            int rejectedDirectionalBuckets,
            int insideSectionFallbacks,
            int missingCameraFallbacks,
            int missingAabbFallbacks,
            int[] bucketRejectedByFaceMask,
            int skippedTranslucentCommands,
            int skippedEmptyBuckets,
            int skippedBucketCommands
    ) {
        this.commands = Collections.unmodifiableList(new ArrayList<>(commands));
        this.heapGeneration = heapGeneration;
        this.dimensionId = dimensionId == null || dimensionId.isBlank() ? "none" : dimensionId;
        this.buildTimeMillis = buildTimeMillis;
        this.recordCount = Math.max(0L, recordCount);
        this.selectionMode = selectionMode == null ? "unknown" : selectionMode;
        this.skippedSections = Math.max(0, skippedSections);
        this.skippedRecords = Math.max(0L, skippedRecords);
        this.planCandidateSections = Math.max(0, planCandidateSections);
        this.planAcceptedSections = Math.max(0, planAcceptedSections);
        this.bucketAware = bucketAware;
        this.includedTranslucent = includedTranslucent;
        this.includedDoubleSided = includedDoubleSided;
        this.includedDirectional = includedDirectional;
        this.directionalFaceMask = directionalFaceMask;
        this.faceMaskFallbackAllWhenInside = faceMaskFallbackAllWhenInside;
        this.faceMaskFallbackReason = faceMaskFallbackReason == null || faceMaskFallbackReason.isBlank() ? "none" : faceMaskFallbackReason;
        this.faceMaskCommandsAccepted = Math.max(0, faceMaskCommandsAccepted);
        this.faceMaskCommandsRejected = Math.max(0, faceMaskCommandsRejected);
        this.rejectedDirectionalBuckets = Math.max(0, rejectedDirectionalBuckets);
        this.insideSectionFallbacks = Math.max(0, insideSectionFallbacks);
        this.missingCameraFallbacks = Math.max(0, missingCameraFallbacks);
        this.missingAabbFallbacks = Math.max(0, missingAabbFallbacks);
        this.bucketRejectedByFaceMask = sanitizeBucketArray(bucketRejectedByFaceMask);
        this.skippedTranslucentCommands = Math.max(0, skippedTranslucentCommands);
        this.skippedEmptyBuckets = Math.max(0, skippedEmptyBuckets);
        this.skippedBucketCommands = Math.max(0, skippedBucketCommands);

        int minRecords = Integer.MAX_VALUE;
        int maxRecords = 0;
        int nonEmptyBuckets = 0;
        int emptyBuckets = 0;
        int maskOr = 0;
        int maskAnd = this.commands.isEmpty() ? 0 : -1;
        int minPtr = Integer.MAX_VALUE;
        int maxPtr = 0;
        long totalRecords = 0L;
        Set<Integer> sectionIds = new HashSet<>();
        Map<Integer, Integer> commandsBySection = new HashMap<>();
        int bucketCommandCount = 0;
        int sectionCommandCount = 0;
        int[] bucketCounts = new int[ForgeGpuGeometryDecodedMetadata.BUCKET_COUNT];
        for (ForgeMdicCommand command : this.commands) {
            int records = Math.max(0, command.recordCount());
            minRecords = Math.min(minRecords, records);
            maxRecords = Math.max(maxRecords, records);
            totalRecords += records;
            sectionIds.add(command.sectionId());
            commandsBySection.merge(command.sectionId(), 1, Integer::sum);
            if (command.bucketMask() == 0) {
                emptyBuckets++;
            } else {
                nonEmptyBuckets++;
            }
            int bucketIndex = command.bucketIndex();
            if (command.isBucketCommand() && bucketIndex >= 0) {
                bucketCommandCount++;
                bucketCounts[bucketIndex]++;
            } else {
                sectionCommandCount++;
            }
            maskOr |= command.bucketMask();
            maskAnd &= command.bucketMask();
            minPtr = Math.min(minPtr, command.geometryPtr());
            maxPtr = Math.max(maxPtr, command.geometryPtr());
        }
        this.minRecordCount = this.commands.isEmpty() ? 0 : minRecords;
        this.maxRecordCount = maxRecords;
        this.avgRecordCount = this.commands.isEmpty() ? 0.0D : (double) totalRecords / (double) this.commands.size();
        this.nonEmptyBucketCommands = nonEmptyBuckets;
        this.emptyBucketCommands = emptyBuckets;
        this.bucketMaskOr = maskOr;
        this.bucketMaskAnd = this.commands.isEmpty() ? 0 : maskAnd;
        this.minGeometryPtr = this.commands.isEmpty() ? 0 : minPtr;
        this.maxGeometryPtr = maxPtr;
        this.sectionCount = sectionIds.size();
        this.bucketCommands = bucketCommandCount;
        this.sectionCommands = sectionCommandCount;
        int minPerSection = Integer.MAX_VALUE;
        int maxPerSection = 0;
        for (int count : commandsBySection.values()) {
            minPerSection = Math.min(minPerSection, count);
            maxPerSection = Math.max(maxPerSection, count);
        }
        this.commandsPerSectionMin = commandsBySection.isEmpty() ? 0 : minPerSection;
        this.commandsPerSectionMax = maxPerSection;
        this.bucketCommandCounts = bucketCounts;
    }

    static ForgeMdicCommandList empty() {
        return EMPTY;
    }

    static ForgeMdicCommandList of(List<ForgeMdicCommand> commands, long heapGeneration, String dimensionId, long recordCount, String selectionMode, int skippedSections, long skippedRecords, int planCandidateSections, int planAcceptedSections) {
        return of(commands, heapGeneration, dimensionId, recordCount, selectionMode, skippedSections, skippedRecords, planCandidateSections, planAcceptedSections, false, false, false, false, false, false, "none", 0, 0, 0, 0, 0, 0, new int[ForgeGpuGeometryDecodedMetadata.BUCKET_COUNT], 0, 0, 0);
    }

    static ForgeMdicCommandList of(
            List<ForgeMdicCommand> commands,
            long heapGeneration,
            String dimensionId,
            long recordCount,
            String selectionMode,
            int skippedSections,
            long skippedRecords,
            int planCandidateSections,
            int planAcceptedSections,
            boolean bucketAware,
            boolean includedTranslucent,
            boolean includedDoubleSided,
            boolean includedDirectional,
            boolean directionalFaceMask,
            boolean faceMaskFallbackAllWhenInside,
            String faceMaskFallbackReason,
            int faceMaskCommandsAccepted,
            int faceMaskCommandsRejected,
            int rejectedDirectionalBuckets,
            int insideSectionFallbacks,
            int missingCameraFallbacks,
            int missingAabbFallbacks,
            int[] bucketRejectedByFaceMask,
            int skippedTranslucentCommands,
            int skippedEmptyBuckets,
            int skippedBucketCommands
    ) {
        if (commands == null || commands.isEmpty()) {
            return EMPTY;
        }
        return new ForgeMdicCommandList(commands, heapGeneration, dimensionId, System.currentTimeMillis(), recordCount, selectionMode, skippedSections, skippedRecords, planCandidateSections, planAcceptedSections, bucketAware, includedTranslucent, includedDoubleSided, includedDirectional, directionalFaceMask, faceMaskFallbackAllWhenInside, faceMaskFallbackReason, faceMaskCommandsAccepted, faceMaskCommandsRejected, rejectedDirectionalBuckets, insideSectionFallbacks, missingCameraFallbacks, missingAabbFallbacks, bucketRejectedByFaceMask, skippedTranslucentCommands, skippedEmptyBuckets, skippedBucketCommands);
    }

    private static int[] sanitizeBucketArray(int[] source) {
        int[] result = new int[ForgeGpuGeometryDecodedMetadata.BUCKET_COUNT];
        if (source == null) {
            return result;
        }
        System.arraycopy(source, 0, result, 0, Math.min(source.length, result.length));
        for (int i = 0; i < result.length; i++) {
            result[i] = Math.max(0, result[i]);
        }
        return result;
    }

    boolean isValid() {
        return !this.commands.isEmpty() && this.recordCount > 0;
    }

    boolean isStale(long currentHeapGeneration) {
        return this.isValid() && this.heapGeneration != currentHeapGeneration;
    }

    boolean isDimensionMismatch(String currentDimension) {
        return this.isValid() && currentDimension != null && !this.dimensionId.equals(currentDimension);
    }

    List<ForgeMdicCommand> commands() {
        return this.commands;
    }

    int commandCount() {
        return this.commands.size();
    }

    long recordCount() {
        return this.recordCount;
    }

    long vertexCount() {
        return this.recordCount * 6L;
    }

    long heapGeneration() {
        return this.heapGeneration;
    }

    String dimensionId() {
        return this.dimensionId;
    }

    long buildTimeMillis() {
        return this.buildTimeMillis;
    }

    String selectionMode() {
        return this.selectionMode;
    }

    int skippedSections() {
        return this.skippedSections;
    }

    long skippedRecords() {
        return this.skippedRecords;
    }

    int planCandidateSections() {
        return this.planCandidateSections;
    }

    int planAcceptedSections() {
        return this.planAcceptedSections;
    }

    int minRecordCount() {
        return this.minRecordCount;
    }

    int maxRecordCount() {
        return this.maxRecordCount;
    }

    double avgRecordCount() {
        return this.avgRecordCount;
    }

    int nonEmptyBucketCommands() {
        return this.nonEmptyBucketCommands;
    }

    int emptyBucketCommands() {
        return this.emptyBucketCommands;
    }

    int bucketMaskOr() {
        return this.bucketMaskOr;
    }

    int bucketMaskAnd() {
        return this.bucketMaskAnd;
    }

    int minGeometryPtr() {
        return this.minGeometryPtr;
    }

    int maxGeometryPtr() {
        return this.maxGeometryPtr;
    }

    boolean bucketAware() {
        return this.bucketAware;
    }

    boolean includedTranslucent() {
        return this.includedTranslucent;
    }

    boolean includedDoubleSided() {
        return this.includedDoubleSided;
    }

    boolean includedDirectional() {
        return this.includedDirectional;
    }

    boolean directionalFaceMask() {
        return this.directionalFaceMask;
    }

    boolean faceMaskFallbackAllWhenInside() {
        return this.faceMaskFallbackAllWhenInside;
    }

    String faceMaskFallbackReason() {
        return this.faceMaskFallbackReason;
    }

    int faceMaskCommandsAccepted() {
        return this.faceMaskCommandsAccepted;
    }

    int faceMaskCommandsRejected() {
        return this.faceMaskCommandsRejected;
    }

    int rejectedDirectionalBuckets() {
        return this.rejectedDirectionalBuckets;
    }

    int insideSectionFallbacks() {
        return this.insideSectionFallbacks;
    }

    int missingCameraFallbacks() {
        return this.missingCameraFallbacks;
    }

    int missingAabbFallbacks() {
        return this.missingAabbFallbacks;
    }

    int bucketRejectedByFaceMask(int bucket) {
        return bucket < 0 || bucket >= this.bucketRejectedByFaceMask.length ? 0 : this.bucketRejectedByFaceMask[bucket];
    }

    int sectionCount() {
        return this.sectionCount;
    }

    int bucketCommands() {
        return this.bucketCommands;
    }

    int sectionCommands() {
        return this.sectionCommands;
    }

    int commandsPerSectionMin() {
        return this.commandsPerSectionMin;
    }

    int commandsPerSectionMax() {
        return this.commandsPerSectionMax;
    }

    int bucketCommandCount(int bucket) {
        return bucket < 0 || bucket >= this.bucketCommandCounts.length ? 0 : this.bucketCommandCounts[bucket];
    }

    int skippedTranslucentCommands() {
        return this.skippedTranslucentCommands;
    }

    int skippedEmptyBuckets() {
        return this.skippedEmptyBuckets;
    }

    int skippedBucketCommands() {
        return this.skippedBucketCommands;
    }
}
