package com.ticketmind.service.impl;

import com.ticketmind.config.AgentProperties;
import com.ticketmind.model.dto.SkillSearchResult;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.thomasvitale.langchain4j.spring.chroma.ChromaEmbeddingStore;
import io.thomasvitale.langchain4j.spring.chroma.client.ChromaClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SkillSearchService {

    private static final double DEFAULT_MIN_SCORE = 0.6D;

    private final AgentProperties agentProperties;
    private final ChromaClient chromaClient;
    private final EmbeddingModel embeddingModel;

    public List<SkillSearchResult> searchSkills(String query, double min_score) {
        return searchSkills(query, agentProperties.getKnowledge().getTopK(), min_score);
    }

    public List<SkillSearchResult> searchSkills(String query) {
        return searchSkills(query, agentProperties.getKnowledge().getTopK(), DEFAULT_MIN_SCORE);
    }

    public List<SkillSearchResult> searchSkills(String query, int topK, double minScore) {
        if (!agentProperties.getChroma().isEnabled() || !StringUtils.hasText(query) || topK <= 0) {
            return List.of();
        }

        Embedding queryEmbedding = embeddingModel.embed(query).content();
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(topK)
                .minScore(minScore)
                .build();
        EmbeddingSearchResult<TextSegment> result = skillEmbeddingStore().search(request);
        List<EmbeddingMatch<TextSegment>> matches = result == null ? List.of() : result.matches();

        return matches.stream()
                .map(this::toResult)
                .toList();
    }

    private EmbeddingStore<TextSegment> skillEmbeddingStore() {
        ChromaEmbeddingStore embeddingStore = ChromaEmbeddingStore.builder()
                .client(chromaClient)
                .collectionName(agentProperties.getChroma().getSkillCollection())
                .build();
        embeddingStore.afterPropertiesSet();
        return embeddingStore;
    }

    private SkillSearchResult toResult(EmbeddingMatch<TextSegment> match) {
        TextSegment segment = match.embedded();
        Metadata metadata = segment == null ? null : segment.metadata();
        String skillName = metadata == null ? null : metadata.getString("skillName");
        String sourcePath = metadata == null ? null : metadata.getString("sourcePath");
        String content = segment == null ? null : segment.text();

        return new SkillSearchResult(
                skillName,
                sourcePath,
                match.score() == null ? 0D : match.score(),
                content
        );
    }
}
