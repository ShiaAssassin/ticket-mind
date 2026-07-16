package com.ticketmind.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketmind.common.BusinessException;
import com.ticketmind.common.ResultCode;
import com.ticketmind.config.AgentProperties;
import com.ticketmind.model.dto.KnowledgeChunkHit;
import com.ticketmind.model.dto.KnowledgeDocumentUploadedEvent;
import com.ticketmind.model.entity.KnowledgeChunk;
import com.ticketmind.repository.KnowledgeChunkRepository;
import com.ticketmind.service.knowledge.KnowledgeChunkProcessor;
import com.ticketmind.service.knowledge.KnowledgeUploadEventPublisher;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.thomasvitale.langchain4j.spring.chroma.ChromaEmbeddingStore;
import io.thomasvitale.langchain4j.spring.chroma.api.AddEmbeddingsRequest;
import io.thomasvitale.langchain4j.spring.chroma.api.CreateCollectionRequest;
import io.thomasvitale.langchain4j.spring.chroma.api.DeleteEmbeddingsRequest;
import io.thomasvitale.langchain4j.spring.chroma.api.GetEmbeddingsRequest;
import io.thomasvitale.langchain4j.spring.chroma.api.GetEmbeddingsResponse;
import io.thomasvitale.langchain4j.spring.chroma.client.ChromaClient;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class KnowledgeService {

    private static final String SOURCE_TYPE_KNOWLEDGE = "knowledge";
    private static final int MAX_FILE_BYTES = 10 * 1024 * 1024;
    private static final int DEFAULT_ADJACENT_CHUNKS = 2;
    private static final int VECTOR_CANDIDATE_MULTIPLIER = 4;
    private static final int MIN_VECTOR_CANDIDATES = 20;
    private static final int DELETE_BATCH_SIZE = 100;

    private final ObjectMapper objectMapper;
    private final AgentProperties agentProperties;
    private final KnowledgeChunkRepository knowledgeChunkRepository;
    private final ChromaClient chromaClient;
    private final EmbeddingModel embeddingModel;
    private final ObjectProvider<KnowledgeUploadEventPublisher> knowledgeUploadEventPublisherProvider;

    private final KnowledgeChunkProcessor processor = new KnowledgeChunkProcessor();

    public List<KnowledgeChunkHit> searchKnowledge(String query) {
        return searchKnowledge(query, agentProperties.getKnowledge().getTopK(), DEFAULT_ADJACENT_CHUNKS);
    }

    public List<KnowledgeChunkHit> searchKnowledge(String query, int topK, int adjacentChunks) {
        if (!StringUtils.hasText(query) || topK <= 0) {
            return List.of();
        }

        List<Double> queryEmbedding = safeEmbedding(query);
        List<KnowledgeChunk> candidates = loadSearchCandidates(query, queryEmbedding, topK);
        if (candidates.isEmpty()) {
            return List.of();
        }

        List<ScoredChunk> topMatches = candidates.stream()
                .map(chunk -> new ScoredChunk(chunk, hybridScore(query, queryEmbedding, chunk)))
                .sorted(Comparator.comparingDouble(ScoredChunk::score).reversed())
                .limit(topK)
                .toList();

        LinkedHashMap<Long, KnowledgeChunkHit> merged = new LinkedHashMap<>();
        int safeAdjacentChunks = Math.max(0, adjacentChunks);
        for (ScoredChunk topMatch : topMatches) {
            int startIndex = Math.max(0, topMatch.chunk().getSourceIndex() - safeAdjacentChunks);
            int endIndex = topMatch.chunk().getSourceIndex() + safeAdjacentChunks;
            List<KnowledgeChunk> window = knowledgeChunkRepository.findBySourceAndSourceIndexBetweenOrderBySourceIndexAsc(
                    topMatch.chunk().getSource(),
                    startIndex,
                    endIndex
            );
            for (KnowledgeChunk chunk : window) {
                merged.compute(chunk.getId(), (ignored, existing) -> {
                    double score = existing == null ? topMatch.score() : Math.max(existing.score(), topMatch.score());
                    return toHit(chunk, score);
                });
            }
        }
        return new ArrayList<>(merged.values());
    }

    @Transactional
    public List<KnowledgeChunk> uploadDocument(String filename, String content) {
        String source = sanitizeSource(filename);
        if (!StringUtils.hasText(content)) {
            throw new BusinessException(ResultCode.MISSING_REQUIRED_PARAMETER, "知识库文档内容不能为空");
        }
        if (content.getBytes(StandardCharsets.UTF_8).length > MAX_FILE_BYTES) {
            throw new BusinessException(ResultCode.REQUEST_BODY_TOO_LARGE, "知识库文档大小超过限制");
        }

        List<String> chunks = processor.chunk(
                content,
                agentProperties.getKnowledge().getChunkSize(),
                agentProperties.getKnowledge().getChunkOverlap()
        );
        if (chunks.isEmpty()) {
            return List.of();
        }

        List<ChunkPayload> payloads = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            String chunkContent = chunks.get(i);
            List<Double> embedding = safeEmbedding(chunkContent);
            payloads.add(new ChunkPayload(
                    new KnowledgeChunk(
                            null,
                            source,
                            i,
                            chunkContent,
                            serializeEmbedding(embedding),
                            null
                    ),
                    embedding
            ));
        }

        try {
            if (agentProperties.getChroma().isEnabled()) {
                ensureKnowledgeCollection();
                deleteKnowledgeEmbeddings(source);
            }
            knowledgeChunkRepository.deleteBySource(source);

            List<KnowledgeChunk> savedChunks = knowledgeChunkRepository.saveAll(
                    payloads.stream().map(ChunkPayload::chunk).toList()
            );
            upsertKnowledgeEmbeddings(savedChunks, payloads);
            publishKnowledgeUploadEvent(filename, source, savedChunks.size());
            return savedChunks;
        } catch (BusinessException ex) {
            if (agentProperties.getChroma().isEnabled()) {
                try {
                    deleteKnowledgeEmbeddings(source);
                } catch (BusinessException cleanupEx) {
                    ex.addSuppressed(cleanupEx);
                }
            }
            throw ex;
        } catch (RuntimeException ex) {
            BusinessException uploadFailure = new BusinessException(ResultCode.UNKNOWN_SERVER_ERROR, "上传知识库文档失败");
            uploadFailure.addSuppressed(ex);
            if (agentProperties.getChroma().isEnabled()) {
                try {
                    deleteKnowledgeEmbeddings(source);
                } catch (BusinessException cleanupEx) {
                    uploadFailure.addSuppressed(cleanupEx);
                }
            }
            throw uploadFailure;
        }
    }

    private List<KnowledgeChunk> loadSearchCandidates(String query, List<Double> queryEmbedding, int topK) {
        List<Long> vectorCandidateIds = searchCandidateIdsByVector(query, queryEmbedding, topK);
        if (!vectorCandidateIds.isEmpty()) {
            Map<Long, KnowledgeChunk> chunkById = knowledgeChunkRepository.findAllById(vectorCandidateIds).stream()
                    .collect(Collectors.toMap(KnowledgeChunk::getId, Function.identity()));
            List<KnowledgeChunk> orderedCandidates = vectorCandidateIds.stream()
                    .map(chunkById::get)
                    .filter(java.util.Objects::nonNull)
                    .toList();
            if (!orderedCandidates.isEmpty()) {
                return orderedCandidates;
            }
        }
        return knowledgeChunkRepository.findAll();
    }

    private List<Long> searchCandidateIdsByVector(String query, List<Double> queryEmbedding, int topK) {
        if (!agentProperties.getChroma().isEnabled() || queryEmbedding.isEmpty()) {
            return List.of();
        }

        try {
            EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                    .queryEmbedding(Embedding.from(toFloatArray(queryEmbedding)))
                    .maxResults(Math.max(topK * VECTOR_CANDIDATE_MULTIPLIER, MIN_VECTOR_CANDIDATES))
                    .build();
            EmbeddingSearchResult<TextSegment> result = knowledgeEmbeddingStore().search(request);
            List<EmbeddingMatch<TextSegment>> matches = result == null ? List.of() : result.matches();
            LinkedHashSet<Long> ids = new LinkedHashSet<>();
            for (EmbeddingMatch<TextSegment> match : matches) {
                Long chunkId = parseChunkId(match);
                if (chunkId != null) {
                    ids.add(chunkId);
                }
            }
            return new ArrayList<>(ids);
        } catch (RuntimeException ex) {
            return List.of();
        }
    }

    private void upsertKnowledgeEmbeddings(List<KnowledgeChunk> savedChunks, List<ChunkPayload> payloads) {
        if (!agentProperties.getChroma().isEnabled()) {
            return;
        }

        Map<Integer, List<Double>> embeddingByIndex = payloads.stream()
                .collect(Collectors.toMap(payload -> payload.chunk().getSourceIndex(), ChunkPayload::embedding));

        AddEmbeddingsRequest request = AddEmbeddingsRequest.builder()
                .ids(savedChunks.stream().map(chunk -> "knowledge:" + chunk.getId()).toList())
                .embeddings(savedChunks.stream()
                        .map(chunk -> toFloatArray(embeddingByIndex.getOrDefault(chunk.getSourceIndex(), List.of())))
                        .toList())
                .metadata(savedChunks.stream().map(this::toKnowledgeMetadata).toList())
                .documents(savedChunks.stream().map(KnowledgeChunk::getContent).toList())
                .build();
        chromaClient.upsertEmbeddings(agentProperties.getChroma().getKnowledgeCollection(), request);
    }

    private Map<String, String> toKnowledgeMetadata(KnowledgeChunk chunk) {
        return Map.of(
                "sourceType", SOURCE_TYPE_KNOWLEDGE,
                "chunkId", String.valueOf(chunk.getId()),
                "source", chunk.getSource(),
                "sourceIndex", String.valueOf(chunk.getSourceIndex())
        );
    }

    private EmbeddingStore<TextSegment> knowledgeEmbeddingStore() {
        ChromaEmbeddingStore embeddingStore = ChromaEmbeddingStore.builder()
                .client(chromaClient)
                .collectionName(agentProperties.getChroma().getKnowledgeCollection())
                .build();
        embeddingStore.afterPropertiesSet();
        return embeddingStore;
    }

    private Long parseChunkId(EmbeddingMatch<TextSegment> match) {
        TextSegment segment = match == null ? null : match.embedded();
        Metadata metadata = segment == null ? null : segment.metadata();
        String chunkId = metadata == null ? null : metadata.getString("chunkId");
        if (!StringUtils.hasText(chunkId)) {
            return null;
        }
        try {
            return Long.valueOf(chunkId);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private void ensureKnowledgeCollection() {
        boolean collectionExists = Objects.requireNonNull(chromaClient.listCollections()).stream()
                .anyMatch(collection -> agentProperties.getChroma().getKnowledgeCollection().equals(collection.name()));
        if (!collectionExists) {
            chromaClient.createCollection(new CreateCollectionRequest(agentProperties.getChroma().getKnowledgeCollection()));
        }
    }

    public void resetKnowledgeCollection() {
        boolean collectionExists = Objects.requireNonNull(chromaClient.listCollections()).stream()
                .anyMatch(collection -> agentProperties.getChroma().getKnowledgeCollection().equals(collection.name()));
        if (collectionExists) {
            chromaClient.deleteCollection(agentProperties.getChroma().getKnowledgeCollection());
        }
        chromaClient.createCollection(new CreateCollectionRequest(agentProperties.getChroma().getKnowledgeCollection()));
    }

    private void deleteKnowledgeEmbeddings(String source) {
        while (true) {
            List<String> ids = findKnowledgeEmbeddingIds(source);
            if (ids.isEmpty()) {
                return;
            }
            chromaClient.deleteEmbeddings(
                    agentProperties.getChroma().getKnowledgeCollection(),
                    new DeleteEmbeddingsRequest(ids)
            );
        }
    }

    private List<String> findKnowledgeEmbeddingIds(String source) {
        GetEmbeddingsRequest request = GetEmbeddingsRequest.builder()
                .where(Map.of(
                        "sourceType", SOURCE_TYPE_KNOWLEDGE,
                        "source", source
                ))
                .limit(DELETE_BATCH_SIZE)
                .offset(0)
                .build();
        GetEmbeddingsResponse response = chromaClient.getEmbeddings(
                agentProperties.getChroma().getKnowledgeCollection(),
                request
        );
        if (response == null || response.ids() == null) {
            return List.of();
        }
        return response.ids().stream()
                .filter(ids -> ids != null && !ids.isEmpty())
                .flatMap(Collection::stream)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private double hybridScore(String query, List<Double> queryEmbedding, KnowledgeChunk chunk) {
        double cosineScore = cosine(queryEmbedding, parseEmbedding(chunk.getEmbeddingJson()));
        double keywordScore = keywordScore(query, chunk.getContent());
        return cosineScore * 0.75D + keywordScore * 0.25D;
    }

    private KnowledgeChunkHit toHit(KnowledgeChunk chunk, double score) {
        return new KnowledgeChunkHit(
                chunk.getId(),
                chunk.getSource(),
                chunk.getContent(),
                score
        );
    }

    private List<Double> safeEmbedding(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        try {
            Response<Embedding> response = embeddingModel.embed(text);
            float[] vectorArray = response.content().vector();
            List<Double> result = new ArrayList<>(vectorArray.length);
            for (float value : vectorArray) {
                result.add((double) value);
            }
            return result;
        } catch (RuntimeException ex) {
            throw new BusinessException(ResultCode.UNKNOWN_SERVER_ERROR, "生成知识向量失败");
        }
    }

    private String serializeEmbedding(List<Double> embedding) {
        if (embedding == null || embedding.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(embedding);
        } catch (RuntimeException | IOException ex) {
            throw new BusinessException(ResultCode.UNKNOWN_SERVER_ERROR, "序列化知识向量失败");
        }
    }

    private List<Double> parseEmbedding(String embeddingJson) {
        if (embeddingJson == null || embeddingJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(embeddingJson, new TypeReference<>() {
            });
        } catch (RuntimeException | IOException ex) {
            throw new BusinessException(ResultCode.UNKNOWN_SERVER_ERROR, "解析知识向量失败");
        }
    }

    private double cosine(List<Double> left, List<Double> right) {
        if (left.isEmpty() || right.isEmpty() || left.size() != right.size()) {
            return 0.0;
        }
        double dot = 0.0;
        double leftNorm = 0.0;
        double rightNorm = 0.0;
        for (int i = 0; i < left.size(); i++) {
            double a = left.get(i);
            double b = right.get(i);
            dot += a * b;
            leftNorm += a * a;
            rightNorm += b * b;
        }
        if (leftNorm == 0.0 || rightNorm == 0.0) {
            return 0.0;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    private double keywordScore(String query, String content) {
        if (!StringUtils.hasText(query) || !StringUtils.hasText(content)) {
            return 0.0;
        }
        String normalizedContent = content.toLowerCase(Locale.ROOT);
        Set<String> terms = extractTerms(query);
        if (terms.isEmpty()) {
            return 0.0;
        }
        long matched = terms.stream()
                .filter(normalizedContent::contains)
                .count();
        return Math.min(1.0, matched / (double) terms.size());
    }

    private Set<String> extractTerms(String query) {
        Set<String> terms = new LinkedHashSet<>();
        String normalizedQuery = query.toLowerCase(Locale.ROOT);
        String[] pieces = normalizedQuery.split("[\\s，。！？、；：,.!?;:()\\[\\]{}\"'`]+");
        for (String piece : pieces) {
            if (!StringUtils.hasText(piece)) {
                continue;
            }
            String term = piece.trim();
            if (term.length() >= 2) {
                terms.add(term);
            }
            if (containsCjk(term) && term.length() > 2) {
                for (int i = 0; i < term.length() - 1; i++) {
                    String bigram = term.substring(i, i + 2);
                    if (!bigram.isBlank()) {
                        terms.add(bigram);
                    }
                }
            }
        }
        return terms;
    }

    private boolean containsCjk(String value) {
        return value.codePoints().anyMatch(codePoint -> Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN);
    }

    private String sanitizeSource(String filename) {
        String source = filename == null || filename.isBlank() ? "uploaded-knowledge" : filename.trim();
        source = source.replaceAll("[\\\\/]+", "-");
        return source.length() > 180 ? source.substring(source.length() - 180) : source;
    }

    private void publishKnowledgeUploadEvent(String filename, String source, int chunkCount) {
        KnowledgeUploadEventPublisher publisher = knowledgeUploadEventPublisherProvider.getIfAvailable();
        if (publisher == null) {
            return;
        }
        publisher.publishAfterCommit(new KnowledgeDocumentUploadedEvent(
                filename,
                source,
                chunkCount,
                OffsetDateTime.now()
        ));
    }

    private float[] toFloatArray(List<Double> values) {
        float[] result = new float[values.size()];
        for (int i = 0; i < values.size(); i++) {
            result[i] = values.get(i).floatValue();
        }
        return result;
    }

    private record ScoredChunk(KnowledgeChunk chunk, double score) {
    }

    private record ChunkPayload(KnowledgeChunk chunk, List<Double> embedding) {
    }
}
