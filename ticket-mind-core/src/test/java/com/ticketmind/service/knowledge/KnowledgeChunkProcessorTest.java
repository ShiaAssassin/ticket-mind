package com.ticketmind.service.knowledge;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import com.knuddels.jtokkit.api.IntArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class KnowledgeChunkProcessorTest {

    private static final Encoding ENCODING =
            Encodings.newLazyEncodingRegistry().getEncoding(EncodingType.CL100K_BASE);

    private final KnowledgeChunkProcessor processor = new KnowledgeChunkProcessor(ENCODING);

    @Test
    void shouldChunkByTokenLimit() {
        String content = """
                TicketMind helps teams coordinate ticketing workflows.
                It stores notes, priorities, and routing hints for later retrieval.
                这是一段中文内容，用来验证分块逻辑在混合语言场景下也会按 token 数切分。
                The processor should stop each chunk before it grows beyond the configured token window.
                """;

        List<String> chunks = processor.chunk(content, 20, 4);

        assertTrue(chunks.size() > 1);
        assertFalse(chunks.isEmpty());
        assertTrue(chunks.stream().allMatch(chunk -> ENCODING.countTokens(chunk) <= 20));
    }

    @Test
    void shouldRetainTokenOverlapBetweenChunks() {
        String content = "alpha beta gamma delta epsilon zeta eta theta iota kappa lambda mu nu xi omicron pi rho sigma tau upsilon phi chi psi omega";

        List<String> chunks = processor.chunk(content, 12, 4);

        assertTrue(chunks.size() > 1);
        for (int i = 0; i < chunks.size() - 1; i++) {
            int sharedTokens = commonSuffixPrefixTokens(chunks.get(i), chunks.get(i + 1));
            assertTrue(sharedTokens > 0 && sharedTokens <= 4);
        }
    }

    private int commonSuffixPrefixTokens(String left, String right) {
        IntArrayList leftTokens = ENCODING.encode(left);
        IntArrayList rightTokens = ENCODING.encode(right);
        int maxOverlap = Math.min(leftTokens.size(), rightTokens.size());
        for (int overlap = maxOverlap; overlap > 0; overlap--) {
            if (matches(leftTokens, leftTokens.size() - overlap, rightTokens, overlap)) {
                return overlap;
            }
        }
        return 0;
    }

    private boolean matches(IntArrayList leftTokens, int leftStart, IntArrayList rightTokens, int length) {
        for (int i = 0; i < length; i++) {
            if (leftTokens.get(leftStart + i) != rightTokens.get(i)) {
                return false;
            }
        }
        return true;
    }
}
