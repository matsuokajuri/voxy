package me.cortex.voxy.forge;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorldImporterNbtParityTest {
    @Test
    void missingOrWrongNumericTagsUseOriginalSentinel() {
        CompoundTag tag = new CompoundTag();
        assertEquals(Integer.MIN_VALUE, WorldImporter.getIntOrSentinel(tag, "xPos"));

        tag.putString("xPos", "0");
        assertEquals(Integer.MIN_VALUE, WorldImporter.getIntOrSentinel(tag, "xPos"));

        tag.putLong("xPos", 42L);
        assertEquals(42, WorldImporter.getIntOrSentinel(tag, "xPos"));
    }

    @Test
    void sectionsMustBeARealListTag() {
        CompoundTag tag = new CompoundTag();
        assertThrows(IllegalStateException.class, () -> WorldImporter.requireList(tag, "sections"));

        tag.putString("sections", "not-a-list");
        assertThrows(IllegalStateException.class, () -> WorldImporter.requireList(tag, "sections"));

        ListTag sections = new ListTag();
        tag.put("sections", sections);
        assertSame(sections, WorldImporter.requireList(tag, "sections"));
    }
}
