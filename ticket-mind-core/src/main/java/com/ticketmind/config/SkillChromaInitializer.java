package com.ticketmind.config;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import io.thomasvitale.langchain4j.spring.chroma.api.AddEmbeddingsRequest;
import io.thomasvitale.langchain4j.spring.chroma.api.CreateCollectionRequest;
import io.thomasvitale.langchain4j.spring.chroma.api.DeleteEmbeddingsRequest;
import io.thomasvitale.langchain4j.spring.chroma.api.GetEmbeddingsRequest;
import io.thomasvitale.langchain4j.spring.chroma.api.GetEmbeddingsResponse;
import io.thomasvitale.langchain4j.spring.chroma.client.ChromaClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@Order(0)
@Slf4j
@RequiredArgsConstructor
public class SkillChromaInitializer implements ApplicationRunner {

    private static final String SKILL_RESOURCE_PATTERN = "classpath*:com/ticketmind/agent/skill/**/SKILL.md";
    private static final String SKILL_FILENAME = "SKILL.md";
    private static final int DELETE_BATCH_SIZE = 100;

    private final AgentProperties agentProperties;
    private final ChromaClient chromaClient;
    private final EmbeddingModel embeddingModel;

    @Override
    public synchronized void run(ApplicationArguments args) throws Exception {
        if (!agentProperties.getChroma().isEnabled()) {
            return;
        }

        String collectionName = agentProperties.getChroma().getSkillCollection();
        resetSkillCollection(collectionName);
        registerAllSkills();
    }

    public boolean isSkillWatchEnabled() {
        return agentProperties.getChroma().isEnabled() && agentProperties.getChroma().isSkillWatchEnabled();
    }

    public Path getSkillRootPath() {
        String configuredPath = agentProperties.getChroma().getSkillRootPath();
        if (!StringUtils.hasText(configuredPath)) {
            return null;
        }

        Path path = Paths.get(configuredPath);
        if (!path.isAbsolute()) {
            path = path.toAbsolutePath();
        }
        return path.normalize();
    }

    public boolean hasWatchableSkillRoot() {
        Path skillRoot = getSkillRootPath();
        return skillRoot != null && Files.isDirectory(skillRoot);
    }

    public boolean isSkillFile(Path path) {
        return path != null
                && Files.isRegularFile(path)
                && SKILL_FILENAME.equals(path.getFileName().toString());
    }

    public synchronized void syncSkill(Path skillFile) {
        if (!agentProperties.getChroma().isEnabled() || !isSkillFile(skillFile)) {
            return;
        }

        try {
            SkillDocument skillDocument = toSkillDocument(skillFile);
            deleteSkillEmbeddings(skillDocument.resourcePath());
            upsertSkillDocument(skillDocument);
            log.info("Synced skill into Chroma: {}", skillDocument.resourcePath());
        } catch (Exception ex) {
            log.error("Failed to sync skill file: {}", skillFile, ex);
        }
    }

    public synchronized void deleteSkill(Path skillFile) {
        if (!agentProperties.getChroma().isEnabled() || skillFile == null) {
            return;
        }

        try {
            String resourcePath = toResourcePath(skillFile);
            deleteSkillEmbeddings(resourcePath);
            log.info("Deleted skill embeddings from Chroma: {}", resourcePath);
        } catch (Exception ex) {
            log.error("Failed to delete skill embeddings for file: {}", skillFile, ex);
        }
    }

    public synchronized void syncSkillsUnder(Path directory) {
        if (!agentProperties.getChroma().isEnabled() || directory == null || !Files.isDirectory(directory)) {
            return;
        }

        try (Stream<Path> stream = Files.walk(directory)) {
            List<Path> skillFiles = stream
                    .filter(this::isSkillFile)
                    .sorted()
                    .toList();
            for (Path skillFile : skillFiles) {
                syncSkill(skillFile);
            }
        } catch (IOException ex) {
            log.error("Failed to scan skill directory: {}", directory, ex);
        }
    }

    private void resetSkillCollection(String collectionName) {
        try {
            chromaClient.deleteCollection(collectionName);
        } catch (Exception ex) {
            log.debug("Skill collection does not exist yet: {}", collectionName);
        }

        chromaClient.createCollection(new CreateCollectionRequest(collectionName));
    }

    private void registerAllSkills() throws IOException {
        Path skillRoot = getSkillRootPath();
        if (skillRoot != null && Files.isDirectory(skillRoot)) {
            registerLocalSkills(skillRoot);
            return;
        }

        registerClasspathSkills();
    }

    private void registerLocalSkills(Path skillRoot) throws IOException {
        try (Stream<Path> stream = Files.walk(skillRoot)) {
            List<Path> skillFiles = stream
                    .filter(this::isSkillFile)
                    .sorted(Comparator.naturalOrder())
                    .toList();
            for (Path skillFile : skillFiles) {
                upsertSkillDocument(toSkillDocument(skillFile));
            }
        }
    }

    private void registerClasspathSkills() throws IOException {
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources(SKILL_RESOURCE_PATTERN);
        for (Resource resource : resources) {
            upsertSkillDocument(toSkillDocument(resource));
        }
    }

    private SkillDocument toSkillDocument(Resource resource) throws IOException {
        String resourcePath = resolveResourcePath(resource);
        String skillName = extractSkillName(resourcePath);
        String skillContent = resource.getContentAsString(StandardCharsets.UTF_8);
        return buildSkillDocument(resourcePath, skillName, skillContent);
    }

    private SkillDocument toSkillDocument(Path skillFile) throws IOException {
        Path normalizedPath = skillFile.toAbsolutePath().normalize();
        String resourcePath = toResourcePath(normalizedPath);
        String skillName = extractSkillName(resourcePath);
        String skillContent = Files.readString(normalizedPath, StandardCharsets.UTF_8);
        return buildSkillDocument(resourcePath, skillName, skillContent);
    }

    private SkillDocument buildSkillDocument(String resourcePath, String skillName, String skillContent) {
        Metadata metadata = new Metadata()
                .put("sourceType", "skill")
                .put("skillName", skillName)
                .put("sourcePath", resourcePath);

        TextSegment segment = TextSegment.from(formatSkillHeader(skillName, resourcePath, skillContent), metadata);
        return new SkillDocument(
                toStableId(resourcePath),
                resourcePath,
                skillName,
                segment,
                Map.of(
                        "sourceType", "skill",
                        "skillName", skillName,
                        "sourcePath", resourcePath
                )
        );
    }

    private void upsertSkillDocument(SkillDocument skillDocument) {
        Embedding embedding = embeddingModel.embed(skillDocument.segment()).content();
        AddEmbeddingsRequest request = AddEmbeddingsRequest.builder()
                .ids(List.of(skillDocument.id()))
                .embeddings(List.of(embedding.vector()))
                .metadata(List.of(skillDocument.metadata()))
                .documents(List.of(skillDocument.segment().text()))
                .build();
        chromaClient.upsertEmbeddings(agentProperties.getChroma().getSkillCollection(), request);
    }

    private void deleteSkillEmbeddings(String resourcePath) {
        while (true) {
            List<String> ids = findSkillEmbeddingIds(resourcePath);
            if (ids.isEmpty()) {
                return;
            }
            chromaClient.deleteEmbeddings(
                    agentProperties.getChroma().getSkillCollection(),
                    new DeleteEmbeddingsRequest(ids)
            );
        }
    }

    private List<String> findSkillEmbeddingIds(String resourcePath) {
        GetEmbeddingsRequest request = GetEmbeddingsRequest.builder()
                .where(Map.of(
                        "sourceType", "skill",
                        "sourcePath", resourcePath
                ))
                .limit(DELETE_BATCH_SIZE)
                .offset(0)
                .build();
        GetEmbeddingsResponse response = chromaClient.getEmbeddings(
                agentProperties.getChroma().getSkillCollection(),
                request
        );
        if (response == null || response.ids() == null) {
            return List.of();
        }
        return response.ids().stream()
                .filter(ids -> ids != null && !ids.isEmpty())
                .flatMap(List::stream)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
    }

    private String resolveResourcePath(Resource resource) throws IOException {
        String uri = resource.getURI().toString();
        int markerIndex = uri.indexOf("/com/ticketmind/agent/skill/");
        if (markerIndex >= 0) {
            return uri.substring(markerIndex + 1);
        }
        return resource.getFilename() == null ? uri : resource.getFilename();
    }

    private String toResourcePath(Path skillFile) {
        String normalized = skillFile.toAbsolutePath().normalize().toString().replace('\\', '/');
        String sourceRootMarker = "/src/main/java/";
        int sourceRootIndex = normalized.indexOf(sourceRootMarker);
        if (sourceRootIndex >= 0) {
            return normalized.substring(sourceRootIndex + sourceRootMarker.length());
        }

        int markerIndex = normalized.indexOf("/com/ticketmind/agent/skill/");
        if (markerIndex >= 0) {
            return normalized.substring(markerIndex + 1);
        }

        return skillFile.getFileName().toString();
    }

    private String extractSkillName(String resourcePath) {
        String normalized = resourcePath.replace('\\', '/');
        String marker = "/agent/skill/";
        int markerIndex = normalized.indexOf(marker);
        if (markerIndex < 0) {
            return normalized;
        }
        String remaining = normalized.substring(markerIndex + marker.length());
        int slashIndex = remaining.indexOf('/');
        return slashIndex >= 0 ? remaining.substring(0, slashIndex) : remaining;
    }

    private String formatSkillHeader(String skillName, String resourcePath, String skillContent) {
        StringBuilder builder = new StringBuilder();
        builder.append("# skill: ").append(skillName).append('\n');
        builder.append("source: ").append(resourcePath).append('\n');
        if (StringUtils.hasText(skillContent)) {
            builder.append('\n').append(skillContent.strip()).append('\n');
        }
        return builder.toString();
    }

    private String toStableId(String resourcePath) {
        return "skill:" + resourcePath.replace('\\', '/');
    }

    private record SkillDocument(
            String id,
            String resourcePath,
            String skillName,
            TextSegment segment,
            Map<String, String> metadata
    ) {
    }
}
