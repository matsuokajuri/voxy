package me.cortex.voxy.common.world.service;

import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.lighting.LayerLightSectionStorage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoxelIngestServiceLightingReadinessTest {
    @Test
    void skyDimensionsKeepTheForgeSkyReadinessContract() {
        assertEquals(LightLayer.SKY, VoxelIngestService.requiredReadinessLayer(true));
        assertFalse(VoxelIngestService.isLightingReadyForIngest(
                false,
                LayerLightSectionStorage.SectionType.LIGHT_ONLY));
        assertTrue(VoxelIngestService.isLightingReadyForIngest(
                false,
                LayerLightSectionStorage.SectionType.LIGHT_AND_DATA));

        VoxelIngestService.IngestStats deferred =
                VoxelIngestService.deferredLightingStats(true, true);
        assertTrue(deferred.deferred());
        assertEquals(1, deferred.missingBlockLightSections());
        assertEquals(1, deferred.missingSkyLightSections());
        assertEquals(1, deferred.deferredLightSections());
    }

    @Test
    void noSkyDimensionsRequireBlockLightAndEnterTheRetryPath() {
        assertEquals(LightLayer.BLOCK, VoxelIngestService.requiredReadinessLayer(false));
        assertFalse(VoxelIngestService.isLightingReadyForIngest(
                false,
                LayerLightSectionStorage.SectionType.LIGHT_ONLY));
        assertTrue(VoxelIngestService.isLightingReadyForIngest(
                false,
                LayerLightSectionStorage.SectionType.LIGHT_AND_DATA));

        VoxelIngestService.IngestStats deferred =
                VoxelIngestService.deferredLightingStats(false, false);
        assertTrue(deferred.deferred());
        assertEquals(1, deferred.missingBlockLightSections());
        assertEquals(0, deferred.missingSkyLightSections());
        assertEquals(1, deferred.deferredLightSections());
    }

    @Test
    void airSectionsRetainTheOriginalClearWithoutLightingBehavior() {
        assertTrue(VoxelIngestService.isLightingReadyForIngest(
                true,
                LayerLightSectionStorage.SectionType.LIGHT_ONLY));
    }
}
