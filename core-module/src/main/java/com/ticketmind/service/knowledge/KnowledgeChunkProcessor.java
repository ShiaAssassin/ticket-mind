package com.ticketmind.service.knowledge;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class KnowledgeChunkProcessor {

    private static final Encoding DEFAULT_ENCODING =
            Encodings.newLazyEncodingRegistry().getEncoding(EncodingType.CL100K_BASE);

    private final Encoding encoding;

    public KnowledgeChunkProcessor() {
        this(DEFAULT_ENCODING);
    }

    KnowledgeChunkProcessor(Encoding encoding) {
        this.encoding = Objects.requireNonNull(encoding, "encoding");
    }

    public List<String> chunk(String content, int chunkSize, int overlap) {
        String text = content.replace("\r\n", "\n").trim();
        if (text.isBlank()) {
            return List.of();
        }
        List<String> chunks = new ArrayList<>();
        int safeSize = Math.max(1, chunkSize);
        int safeOverlap = Math.max(0, Math.min(overlap, safeSize / 2));
        int index = 0;
        while (index < text.length()) {
            int end = findChunkEnd(text, index, safeSize);
            int boundary = findBoundary(text, index, end);
            if (boundary > index && countTokens(text, index, boundary) >= Math.max(1, safeSize / 2)) {
                end = boundary;
            }
            chunks.add(text.substring(index, end).trim());
            if (end >= text.length()) {
                break;
            }
            int nextIndex = safeOverlap == 0 ? end : findOverlapStart(text, index, end, safeOverlap);
            if (nextIndex <= index || nextIndex >= end) {
                nextIndex = end;
            }
            index = skipLeadingWhitespace(text, nextIndex);
        }
        return chunks;
    }

    private int findChunkEnd(String text, int start, int tokenLimit) {
        if (countTokens(text, start, text.length()) <= tokenLimit) {
            return text.length();
        }
        int low = start + 1;
        int high = text.length();
        int best = start;
        while (low <= high) {
            int mid = low + (high - low) / 2;
            if (countTokens(text, start, mid) <= tokenLimit) {
                best = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        if (best > start) {
            return best;
        }
        return Math.min(text.length(), start + Character.charCount(text.codePointAt(start)));
    }

    private int findOverlapStart(String text, int chunkStart, int chunkEnd, int overlapTokens) {
        int low = chunkStart;
        int high = chunkEnd;
        int best = chunkEnd;
        while (low <= high) {
            int mid = low + (high - low) / 2;
            if (countTokens(text, mid, chunkEnd) <= overlapTokens) {
                best = mid;
                high = mid - 1;
            } else {
                low = mid + 1;
            }
        }
        return best;
    }

    private int findBoundary(String text, int start, int end) {
        for (int i = end - 1; i >= start; i--) {
            char current = text.charAt(i);
            if (isBoundary(current)) {
                return i + 1;
            }
        }
        return -1;
    }

    private boolean isBoundary(char value) {
        return value == '\n'
                || value == '.'
                || value == '?'
                || value == '!'
                || value == '。'
                || value == '？'
                || value == '！'
                || value == ';'
                || value == '；';
    }

    private int skipLeadingWhitespace(String text, int index) {
        int current = index;
        while (current < text.length() && Character.isWhitespace(text.charAt(current))) {
            current++;
        }
        return current;
    }

    private int countTokens(String text, int start, int end) {
        return encoding.countTokens(text.substring(start, end));
    }
}
