package me.cortex.voxy.forge;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class Round11AsyncRuntimeAuditTest {
    @Test
    void fixedSampleRingsRetainRecentPercentilesAndLifetimeMaxWithoutGrowing() throws Exception {
        Object audit = create();
        Object active = get(audit, "active"), publish = get(audit, "publish");
        Method record = method(audit, "record", long.class, long.class, int.class);
        for (int sample = 1; sample <= 4096; sample++) record.invoke(audit, (long) sample, (long) sample / 2, 3);
        assertSame(active, get(audit, "active"));
        assertSame(publish, get(audit, "publish"));
        assertEquals(2048, ((long[]) active).length);
        String summary = (String) method(audit, "summary").invoke(audit);
        assertTrue(summary.contains("runs=4096, events=12288, recentSamples=2048"), summary);
        assertTrue(summary.contains("activeNs[p50=3072, p95=3994, max=4096]"), summary);
        assertTrue(summary.contains("publishNs[p50=1536, p95=1997, max=2048]"), summary);
    }

    @Test
    void emptySummaryIsDefinedAndReportingIsLimitedToFiveSecondIntervals() throws Exception {
        Object audit = create();
        String summary = (String) method(audit, "summary").invoke(audit);
        assertTrue(summary.contains("recentSamples=0"));
        assertTrue(summary.contains("p50=0, p95=0, max=0"));
        Method due = method(audit, "reportDue", long.class);
        long deadline = (long) get(audit, "nextReport");
        assertEquals(false, due.invoke(audit, deadline - 1));
        assertEquals(true, due.invoke(audit, deadline));
        assertEquals(false, due.invoke(audit, deadline + 4_999_999_999L));
        assertEquals(true, due.invoke(audit, deadline + 5_000_000_000L));
    }

    private static Object create() throws Exception {
        Class<?> type = Class.forName("me.cortex.voxy.forge.AsyncNodeManager$RuntimeAudit");
        Constructor<?> constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private static Method method(Object object, String name, Class<?>... arguments) throws Exception {
        Method method = object.getClass().getDeclaredMethod(name, arguments);
        method.setAccessible(true);
        return method;
    }

    private static Object get(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }
}
