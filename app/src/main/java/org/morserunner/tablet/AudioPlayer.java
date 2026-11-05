// SPDX-License-Identifier: MPL-2.0
package org.morserunner.tablet;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Process;
import java.util.function.Consumer;

final class AudioPlayer {
    private volatile boolean stopped;
    private volatile AudioTrack track;
    private final Thread worker;
    AudioPlayer(NativeEngine engine, Runnable completed, Consumer<String> failed) {
        worker = new Thread(() -> {
            try {
                Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
                int minimum = AudioTrack.getMinBufferSize(48000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
                if (minimum <= 0) throw new IllegalStateException("设备不支持 48 kHz PCM 输出");
                AudioTrack audio = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setAudioFormat(new AudioFormat.Builder().setSampleRate(48000).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                    .setBufferSizeInBytes(Math.max(minimum, 4096)).setTransferMode(AudioTrack.MODE_STREAM)
                    .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY).build();
                track = audio;
                if (audio.getState() != AudioTrack.STATE_INITIALIZED) throw new IllegalStateException("音频输出初始化失败");
                if (stopped) return;
                audio.play();
                short[] pcm = new short[1024];
                while (!stopped) {
                    boolean active = engine.fill(pcm);
                    int offset = 0;
                    while (offset < pcm.length && !stopped) {
                        int written = audio.write(pcm, offset, pcm.length - offset, AudioTrack.WRITE_BLOCKING);
                        if (written <= 0) throw new IllegalStateException("音频输出中断: " + written);
                        offset += written;
                    }
                    if (!active) { completed.run(); break; }
                }
            } catch (RuntimeException exception) {
                if (!stopped) failed.accept(exception.getMessage());
            } finally {
                AudioTrack audio = track;
                track = null;
                if (audio != null) {
                    try { audio.pause(); audio.flush(); } catch (IllegalStateException ignored) { }
                    audio.release();
                }
            }
        }, "MorseRunner-Audio");
    }
    void start() { worker.start(); }
    void stop() {
        stopped = true;
        AudioTrack audio = track;
        if (audio != null) { try { audio.pause(); } catch (IllegalStateException ignored) { } }
        try { worker.join(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
