// SPDX-License-Identifier: MPL-2.0
package org.morserunner.tablet;

import java.util.Arrays;

public final class TrainingProfileTest {
    private static void rejects(String call, int tx, int low, int high, int mode, int library) {
        try { new TrainingProfile(call, tx, low, high, mode, library); }
        catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Invalid setup was accepted");
    }
    public static void main(String[] args) {
        TrainingProfile p = new TrainingProfile(" bd6iut/qrp ", 80, 12, 25, 3, 2);
        if (!p.call.equals("BD6IUT/QRP")) throw new AssertionError("Call normalization");
        int[] expected = {80, 3, 10, 4, 600, 500, 32, -20, 12, 25};
        if (!Arrays.equals(expected, p.options(3, 10, 600, 500, 32, -20))) throw new AssertionError("JNI options contract");
        new TrainingProfile("K1ABC", 10, 5, 5, 0, 0);
        new TrainingProfile("K1ABC", 120, 120, 120, 3, 3);
        for (String call : new String[]{"", "ABC", "1234", "K1 ABC", "/K1ABC", "K1ABC/", "K1?"}) rejects(call,30,15,30,0,0);
        rejects("K1ABC",9,15,30,0,0); rejects("K1ABC",121,15,30,0,0);
        rejects("K1ABC",30,4,30,0,0); rejects("K1ABC",30,20,121,0,0);
        rejects("K1ABC",30,40,20,0,0); rejects("K1ABC",30,15,30,4,0);
        rejects("K1ABC",30,15,30,0,4);
        System.out.println("PASS: setup call validation, independent speeds, fixed speed, mode/library bounds and JNI option mapping");
    }
}
