// SPDX-License-Identifier: MPL-2.0
// ARRL.pas / Log.pas lookup behavior, using the same 1.71 ARRL.LIST.
package org.morserunner.tablet;
import java.util.*;
import java.util.regex.*;

final class CallsignInfo {
    private static final Pattern CALL = Pattern.compile("(([0-9][A-Z])|([A-Z]{1,2}))[0-9][A-Z0-9]*[A-Z]");
    private static final Pattern PREFIX = Pattern.compile("(([0-9][A-Z])|([A-Z]{1,2}))[0-9]");
    private final List<Pattern> patterns = new ArrayList<>();
    private final List<String> descriptions = new ArrayList<>();
    CallsignInfo(String data) {
        String[] rows = data.split("\\r?\\n"); Arrays.sort(rows);
        for (String row : rows) {
            String[] fields = row.split(";", -1);
            if (fields.length != 7) continue;
            try { patterns.add(Pattern.compile("^(" + fields[1] + ")")); descriptions.add(fields[2] + "/" + fields[3] + " · ITU " + fields[4] + " · CQ " + fields[5]); }
            catch (PatternSyntaxException ignored) { }
        }
    }
    String lookup(String call) {
        Matcher c = CALL.matcher(call); if (!c.find() || (c.start() > 0 && call.charAt(c.start() - 1) != '/')) return "";
        String actual = c.group(); Matcher p = PREFIX.matcher(actual); if (!p.find()) return "";
        String prefix = p.group();
        for (int i = patterns.size() - 1; i >= 0; i--) if (patterns.get(i).matcher(prefix).find()) return actual + " · " + descriptions.get(i);
        return "";
    }
}
