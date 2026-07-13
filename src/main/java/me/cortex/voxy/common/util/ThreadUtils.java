package me.cortex.voxy.common.util;

import me.cortex.voxy.common.Logger;
import org.lwjgl.system.*;
import org.lwjgl.system.windows.WinBase;

//Platform specific code to assist in thread utilities
public class ThreadUtils {
    public static final int WIN32_THREAD_PRIORITY_TIME_CRITICAL = 15;
    public static final int WIN32_THREAD_PRIORITY_LOWEST = -2;
    public static final int WIN32_THREAD_MODE_BACKGROUND_BEGIN = 0x00010000;
    public static final int WIN32_THREAD_MODE_BACKGROUND_END = 0x00020000;
    public static final boolean isWindows = Platform.get() == Platform.WINDOWS;
    public static final boolean isLinux = Platform.get() == Platform.LINUX;
    private static final long GetCurrentThread;
    private static final long SetThreadPriority;
    private static final long SetThreadSelectedCpuSetMasks;
    private static final long schedSetaffinity;
    static {
        if (isWindows) {
            long kernel32 = WinBase.GetModuleHandle("kernel32.dll");
            GetCurrentThread = kernel32 == 0 ? 0 : WinBase.GetProcAddress(kernel32, "GetCurrentThread");
            SetThreadPriority = kernel32 == 0 ? 0 : WinBase.GetProcAddress(kernel32, "SetThreadPriority");
            SetThreadSelectedCpuSetMasks = kernel32 == 0
                    ? 0
                    : WinBase.GetProcAddress(kernel32, "SetThreadSelectedCpuSetMasks");
        } else {
            GetCurrentThread = 0;
            SetThreadPriority = 0;
            SetThreadSelectedCpuSetMasks = 0;
        }

        if (Platform.get() == Platform.LINUX) {
            long fn = 0;
            try {
                var libc = APIUtil.apiCreateLibrary("libc.so.6");
                fn = APIUtil.apiGetFunctionAddress(libc, "sched_setaffinity");
            } catch (Exception e) {
                Logger.error(e);
            }
            schedSetaffinity = fn;
        } else {
            schedSetaffinity = 0;
        }
    }

    public static boolean SetThreadSelectedCpuSetMasksWin32(long mask) {
        return SetThreadSelectedCpuSetMasksWin32(new long[]{mask}, new short[]{0});
    }

    public static boolean SetThreadSelectedCpuSetMasksWin32(long[] masks, short[] groups) {
        if (GetCurrentThread == 0 || SetThreadSelectedCpuSetMasks == 0 || !isWindows) {
            return false;
        }
        long currentThread = JNI.callP(GetCurrentThread);
        if (currentThread == 0) {
            return false;
        }

        if (masks == null) {
            int retVal = JNI.callPPI(currentThread, 0L, (short) 0, SetThreadSelectedCpuSetMasks);
            if (retVal == 0) {
                throw new IllegalStateException();
            }
            return true;
        }

        if (masks.length != groups.length) {
            throw new IllegalArgumentException();
        }
        try (var stack = MemoryStack.stackPush()) {
            long ptr = stack.ncalloc(16, masks.length, 16);
            MemoryUtil.memSet(ptr, 0, masks.length*16L);
            for (int i = 0; i < masks.length; i++) {
                MemoryUtil.memPutLong(ptr+i*16L, masks[i]);
                MemoryUtil.memPutShort(ptr+i*16L+8L, groups[i]);
            }

            int retVal = JNI.callPPI(currentThread, ptr, (short)masks.length, SetThreadSelectedCpuSetMasks);
            if (retVal == 0) {
                throw new IllegalStateException();
            }
            return true;
        }
    }

    public static boolean SetSelfThreadPriorityWin32(int priority) {
        if (GetCurrentThread == 0 || SetThreadPriority == 0 || !isWindows) {
            return false;
        }
        long currentThread = JNI.callP(GetCurrentThread);
        if (currentThread == 0) {
            return false;
        }
        if (JNI.callPI(currentThread, priority, SetThreadPriority)==0) {
            throw new IllegalStateException("Operation failed");
        }
        return true;
    }

    public static boolean schedSetaffinityLinux(long masks[]) {
        if (schedSetaffinity == 0 || isWindows) {
            return false;
        }
        try (var stack = MemoryStack.stackPush()) {
            long ptr = stack.ncalloc(8, masks.length, 8);
            for (int i=0; i<masks.length; i++) {
                MemoryUtil.memPutLong(ptr+i*8L, masks[i]);
            }

            int retVal = JNI.invokePPI(0, (long)masks.length*8, ptr, schedSetaffinity);
            if (retVal != 0) {
                throw new IllegalStateException();
            }
            return true;
        }
    }
}
