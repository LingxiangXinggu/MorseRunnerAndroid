// SPDX-License-Identifier: MPL-2.0
package org.morserunner.tablet;
import java.nio.file.*;
public final class CallsignInfoTest {
    public static void main(String[] args) throws Exception {
        CallsignInfo info = new CallsignInfo(Files.readString(Path.of(args[0])));
        if (!info.lookup("BD6IUT").contains("China")) throw new AssertionError(info.lookup("BD6IUT"));
        if (!info.lookup("K1ABC").contains("United States")) throw new AssertionError(info.lookup("K1ABC"));
        if (!info.lookup("?").isEmpty()) throw new AssertionError("Unrecognized call accepted");
        if (!info.lookup("EA8/K1ABC").contains("United States")) throw new AssertionError("1.71 extraction order changed");
        System.out.println("PASS: Java callsign/country lookup matches 1.71 extraction and ARRL.LIST");
    }
}
