package com.limelight.brp;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Splits text typed on the computer through the protocol's UTF-8 text channel.
 *
 * - Each packet carries at most {@link #MAX_BYTES} bytes, the size of the
 *   protocol's text field, and is never split inside a character.
 * - The JNI bridge converts strings with GetStringUTFChars (modified UTF-8), which
 *   would turn characters outside the Basic Multilingual Plane (most emoji) into
 *   invalid surrogate bytes on the host; they are left out and counted.
 * - NUL and other control characters except tab and newline are left out.
 * - At most {@link #MAX_CHARS} characters, so one paste cannot flood the input
 *   queue (150 packets in moonlight-common-c).
 *
 * Pure Java, unit-tested.
 */
public final class TextChunker {
    public static final int MAX_BYTES = 32;
    public static final int MAX_CHARS = 2000;

    public final List<String> chunks;
    public final int sentChars;
    public final int skippedChars;
    public final boolean truncated;

    private TextChunker(List<String> chunks, int sent, int skipped, boolean truncated) {
        this.chunks = chunks;
        this.sentChars = sent;
        this.skippedChars = skipped;
        this.truncated = truncated;
    }

    public static TextChunker split(CharSequence text) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        int curBytes = 0, sent = 0, skipped = 0;
        boolean truncated = false;

        int i = 0;
        while (i < text.length()) {
            int cp = Character.codePointAt(text, i);
            i += Character.charCount(cp);

            if (sent >= MAX_CHARS) {
                truncated = true;
                break;
            }
            if (cp > 0xFFFF || (cp < 0x20 && cp != '\t' && cp != '\n') || cp == 0x7F) {
                skipped++;
                continue;
            }
            String ch = new String(Character.toChars(cp));
            int bytes = ch.getBytes(StandardCharsets.UTF_8).length;
            if (curBytes + bytes > MAX_BYTES) {
                out.add(cur.toString());
                cur.setLength(0);
                curBytes = 0;
            }
            cur.append(ch);
            curBytes += bytes;
            sent++;
        }
        if (cur.length() > 0) {
            out.add(cur.toString());
        }
        return new TextChunker(out, sent, skipped, truncated);
    }
}
