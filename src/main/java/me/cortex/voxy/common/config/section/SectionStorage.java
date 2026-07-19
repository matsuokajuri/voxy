package me.cortex.voxy.common.config.section;

import me.cortex.voxy.common.config.IMappingStorage;
import me.cortex.voxy.common.config.IStoredSectionPositionIterator;
import me.cortex.voxy.common.world.WorldSection;

public abstract class SectionStorage implements IMappingStorage, IStoredSectionPositionIterator {
    public static final int LOAD_CORRUPT = -1;
    public static final int LOAD_UNAVAILABLE = -2;
    public static final int LOAD_OK = 0;
    public static final int LOAD_MISSING = 1;
    public static final int LOAD_RECOVERED = 2;

    public abstract int loadSection(WorldSection into);

    public abstract void saveSection(WorldSection section);
}
