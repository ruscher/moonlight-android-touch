package com.limelight.brp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

public class TextChunkerTest {
    @Test
    public void shortTextIsOneChunk() {
        TextChunker c = TextChunker.split("olá mundo");
        assertEquals(1, c.chunks.size());
        assertEquals("olá mundo", c.chunks.get(0));
        assertEquals(9, c.sentChars);
        assertFalse(c.truncated);
    }

    @Test
    public void chunksNeverExceed32BytesNorSplitCharacters() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 50; i++) {
            sb.append("ação"); // multi-byte characters
        }
        TextChunker c = TextChunker.split(sb);
        StringBuilder joined = new StringBuilder();
        for (String chunk : c.chunks) {
            assertTrue(chunk.getBytes(StandardCharsets.UTF_8).length <= TextChunker.MAX_BYTES);
            joined.append(chunk);
        }
        assertEquals(sb.toString(), joined.toString());
    }

    @Test
    public void emojiAndControlCharactersAreLeftOut() {
        TextChunker c = TextChunker.split("a😀b\u0000c\nd");
        assertEquals("abc\nd", String.join("", c.chunks));
        assertEquals(2, c.skippedChars);
    }

    @Test
    public void longTextIsTruncated() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < TextChunker.MAX_CHARS + 10; i++) {
            sb.append('x');
        }
        TextChunker c = TextChunker.split(sb);
        assertEquals(TextChunker.MAX_CHARS, c.sentChars);
        assertTrue(c.truncated);
    }
}
