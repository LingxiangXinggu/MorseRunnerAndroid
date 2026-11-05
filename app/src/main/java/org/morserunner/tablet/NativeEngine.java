// SPDX-License-Identifier: MPL-2.0
package org.morserunner.tablet;

final class NativeEngine implements AutoCloseable {
    static { System.loadLibrary("morserunner"); }
    private long handle;
    NativeEngine(String call, String data, int[] options) { handle = create(call, data, options); }
    synchronized boolean fill(short[] pcm) { return handle != 0 && render(handle, pcm); }
    synchronized boolean command(int command, String call, int rst, int nr) { return handle != 0 && action(handle, command, call, rst, nr); }
    synchronized String state() { return handle == 0 ? "{}" : snapshot(handle); }
    synchronized String export() { return handle == 0 ? "" : csv(handle); }
    @Override public synchronized void close() { if (handle != 0) { destroy(handle); handle = 0; } }
    private static native long create(String call, String data, int[] options);
    private static native void destroy(long handle);
    private static native boolean render(long handle, short[] pcm);
    private static native boolean action(long handle, int command, String call, int rst, int nr);
    private static native String snapshot(long handle);
    private static native String csv(long handle);
}
