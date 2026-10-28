package com.star4droid.star2d.remote;

import com.google.gson.JsonObject;

import java.io.ByteArrayOutputStream;
import java.util.Random;

/** Tiny synthesizer: 16-bit mono WAV effects without any uploads. */
final class SoundGen {
    private SoundGen() {}

    private static final int RATE = 22050;

    private static final class Seg {
        final String wave;
        final double f0, f1, dur, vol;

        Seg(String wave, double f0, double f1, double dur, double vol) {
            this.wave = wave;
            this.f0 = f0;
            this.f1 = f1;
            this.dur = dur;
            this.vol = vol;
        }
    }

    static byte[] wav(JsonObject a) {
        String preset = CoreTools.s(a, "preset");
        double volume = a.has("volume") ? Math.max(0, Math.min(1, a.get("volume").getAsDouble())) : 0.8;
        Seg[] segs;
        double attack = a.has("attack") ? a.get("attack").getAsDouble() : 0.005;
        double decay = -1;
        switch (preset) {
            case "jump": segs = new Seg[]{new Seg("square", 220, 700, 0.22, 1)}; break;
            case "coin": segs = new Seg[]{new Seg("square", 988, 988, 0.07, 1), new Seg("square", 1319, 1319, 0.28, 1)}; break;
            case "hit": segs = new Seg[]{new Seg("noise", 300, 60, 0.16, 1)}; break;
            case "explosion": segs = new Seg[]{new Seg("noise", 220, 30, 0.7, 1)}; decay = 0.7; break;
            case "shoot": segs = new Seg[]{new Seg("saw", 900, 120, 0.2, 1)}; break;
            case "powerup": segs = new Seg[]{new Seg("square", 330, 440, 0.08, 1), new Seg("square", 440, 660, 0.08, 1), new Seg("square", 660, 880, 0.08, 1), new Seg("square", 880, 1320, 0.2, 1)}; break;
            case "click": segs = new Seg[]{new Seg("square", 1000, 800, 0.04, 1)}; break;
            case "laser": segs = new Seg[]{new Seg("saw", 1800, 200, 0.25, 1)}; break;
            case "blip": segs = new Seg[]{new Seg("sine", 600, 600, 0.09, 1)}; break;
            case "": {
                double f0 = a.has("freq") ? a.get("freq").getAsDouble() : 440;
                double f1 = a.has("freqEnd") ? a.get("freqEnd").getAsDouble() : f0;
                double dur = a.has("duration") ? a.get("duration").getAsDouble() : 0.3;
                String wave = CoreTools.s(a, "wave");
                segs = new Seg[]{new Seg(wave.isEmpty() ? "sine" : wave, f0, f1, Math.max(0.01, Math.min(5, dur)), 1)};
                if (a.has("decay")) decay = a.get("decay").getAsDouble();
                break;
            }
            default:
                throw new Gl.ToolError("unknown preset '" + preset + "'. Use jump|coin|hit|explosion|shoot|powerup|click|laser|blip, or give wave/freq/freqEnd/duration");
        }
        if (a.has("duration") && !preset.isEmpty() && segs.length == 1)
            segs = new Seg[]{new Seg(segs[0].wave, segs[0].f0, segs[0].f1, Math.max(0.01, Math.min(5, a.get("duration").getAsDouble())), 1)};
        if (a.has("decay")) decay = a.get("decay").getAsDouble();

        double total = 0;
        for (Seg s : segs) total += s.dur;
        int n = (int) (total * RATE);
        short[] pcm = new short[n];
        Random rnd = new Random(7);
        int pos = 0;
        double phase = 0, lastNoise = 0, noiseHold = 0;
        for (Seg s : segs) {
            int len = (int) (s.dur * RATE);
            for (int i = 0; i < len && pos < n; i++, pos++) {
                double t = i / (double) len;
                double f = s.f0 + (s.f1 - s.f0) * t;
                phase += f / RATE;
                phase -= Math.floor(phase);
                double v;
                switch (s.wave) {
                    case "square": v = phase < 0.5 ? 1 : -1; break;
                    case "saw": v = 2 * phase - 1; break;
                    case "triangle": v = 4 * Math.abs(phase - 0.5) - 1; break;
                    case "noise": {
                        // low-passed noise whose cutoff follows the frequency
                        double k = Math.min(1, f / RATE * 6);
                        lastNoise += (rnd.nextDouble() * 2 - 1 - lastNoise) * k;
                        v = lastNoise * 2.2;
                        break;
                    }
                    default: v = Math.sin(2 * Math.PI * phase);
                }
                double tt = i / (double) RATE;
                double env = 1;
                if (attack > 0 && tt < attack) env = tt / attack;
                double rest = s.dur - tt;
                double d = decay > 0 ? decay : Math.min(0.12, s.dur * 0.6);
                if (rest < d) env *= rest / d;
                v = Math.max(-1, Math.min(1, v * env * s.vol * volume));
                pcm[pos] = (short) (v * 32000);
            }
        }
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        int dataLen = n * 2;
        writeStr(bo, "RIFF");
        writeInt(bo, 36 + dataLen);
        writeStr(bo, "WAVEfmt ");
        writeInt(bo, 16);
        writeShort(bo, 1);
        writeShort(bo, 1);
        writeInt(bo, RATE);
        writeInt(bo, RATE * 2);
        writeShort(bo, 2);
        writeShort(bo, 16);
        writeStr(bo, "data");
        writeInt(bo, dataLen);
        for (short s : pcm) writeShort(bo, s);
        return bo.toByteArray();
    }

    private static void writeStr(ByteArrayOutputStream o, String s) {
        for (char c : s.toCharArray()) o.write(c);
    }

    private static void writeInt(ByteArrayOutputStream o, int v) {
        o.write(v & 255);
        o.write((v >> 8) & 255);
        o.write((v >> 16) & 255);
        o.write((v >> 24) & 255);
    }

    private static void writeShort(ByteArrayOutputStream o, int v) {
        o.write(v & 255);
        o.write((v >> 8) & 255);
    }
}
