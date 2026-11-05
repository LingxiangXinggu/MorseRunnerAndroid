// SPDX-License-Identifier: MPL-2.0
package org.morserunner.tablet;

public final class NativeEngineIntegrationTest {
    private static void rejects(int[] options) {
        try (NativeEngine ignored = new NativeEngine("BD6IUT", "K1ABC\n", options)) {
            throw new AssertionError("Invalid JNI configuration accepted");
        } catch (IllegalStateException expected) { }
    }
    public static void main(String[] args) {
        try (NativeEngine engine = new NativeEngine("BD6IUT", "K1ABC\n", new int[]{80,3,1,2,600,500,32,-20,12,20})) {
            short[] pcm = new short[1024];
            for (int i = 0; i < 100; i++) if (!engine.fill(pcm)) throw new AssertionError("Session ended prematurely");
            if (!engine.state().contains("\"wpm\":80")) throw new AssertionError("TX speed not forwarded");
            engine.command(6,"",110,0);
            if (!engine.state().contains("\"wpm\":110")) throw new AssertionError("TX tuning failed");
        }
        try (NativeEngine legacy = new NativeEngine("BD6IUT", "K1ABC\n", new int[]{30,3,1,2,600,500,32,-20})) {
            if (!legacy.fill(new short[1024])) throw new AssertionError("Legacy eight-option caller broke");
        }
        rejects(new int[]{30,3,1,2,600,500,32,-20,40,20});
        rejects(new int[]{30,3,1,2,600,500,32,-20,12});
        System.out.println("PASS: actual host JNI ten-option configuration, audio rendering, TX tuning, legacy eight-option support and rejected invalid ranges");
    }
}
