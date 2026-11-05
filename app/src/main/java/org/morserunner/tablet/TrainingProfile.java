// SPDX-License-Identifier: MPL-2.0
package org.morserunner.tablet;

import java.util.Locale;

/** Validated basics shared by setup, settings and the JNI configuration. */
final class TrainingProfile {
    final String call;
    final int txWpm, dxMin, dxMax, mode, library;

    TrainingProfile(String call, int txWpm, int dxMin, int dxMax, int mode, int library) {
        String normalized = call.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() < 3 || normalized.length() > 24
                || !normalized.matches("[A-Z0-9]+(/[A-Z0-9]+)*")
                || !normalized.matches(".*[A-Z].*") || !normalized.matches(".*[0-9].*"))
            throw new IllegalArgumentException("请填写包含字母和数字的有效呼号，例如 BD6IUT");
        if (txWpm < 10 || txWpm > 120) throw new IllegalArgumentException("本台速度范围为 10–120 WPM");
        if (dxMin < 5 || dxMax > 120 || dxMin > dxMax)
            throw new IllegalArgumentException("其他电台速度须在 5–120 WPM 内，最低速度不能高于最高速度");
        if (mode < 0 || mode > 3 || library < 0 || library > 3)
            throw new IllegalArgumentException("请选择训练模式和呼号库");
        this.call = normalized; this.txWpm = txWpm; this.dxMin = dxMin;
        this.dxMax = dxMax; this.mode = mode; this.library = library;
    }

    int[] options(int activity, int duration, int pitch, int bandwidth, int flags, int monitor) {
        return new int[]{txWpm, activity, duration, mode + 1, pitch, bandwidth, flags, monitor, dxMin, dxMax};
    }
}
