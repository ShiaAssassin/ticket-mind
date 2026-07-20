package com.ticketmind.config;

import com.ticketmind.repository.KnowledgeChunkRepository;
import com.ticketmind.service.impl.KnowledgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@Component
@Order(1)
@Slf4j
@RequiredArgsConstructor
public class KnowledgeBaseInitializer implements ApplicationRunner {

    private static final String KNOWLEDGE_RESOURCE_PATTERN = "classpath*:knowledge/*.*";
    private static final int MAX_FILE_BYTES = 10 * 1024 * 1024;

    private final AgentProperties agentProperties;
    private final KnowledgeChunkRepository knowledgeChunkRepository;
    private final KnowledgeService knowledgeService;

    @Override
    public void run(ApplicationArguments args) {
        int initialized = initializeKnowledgeBaseIfEmpty();
        if (initialized > 0) {
            log.info("Initialized knowledge base with {} classpath documents", initialized);
        }
    }

    public int initializeKnowledgeBaseIfEmpty() {
        if (knowledgeChunkRepository.count() > 0) {
            return 0;
        }

        if (agentProperties.getChroma().isEnabled()) {
            knowledgeService.resetKnowledgeCollection();
        }

        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources;
        try {
            resources = resolver.getResources(KNOWLEDGE_RESOURCE_PATTERN);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to scan knowledge resources", ex);
        }

        List<Resource> sortedResources = Stream.of(resources)
                .filter(Resource::exists)
                .sorted(Comparator.comparing(this::resourceName))
                .toList();

        int initialized = 0;
        for (Resource resource : sortedResources) {
            try {
                knowledgeService.uploadDocument(resourceName(resource), readResourceContent(resource));
                initialized++;
            } catch (Exception ex) {
                log.error("Failed to initialize knowledge resource {}", resourceName(resource), ex);
            }
        }
        return initialized;
    }

    private String resourceName(Resource resource) {
        String filename = resource.getFilename();
        String source = filename == null || filename.isBlank() ? "uploaded-knowledge" : filename.trim();
        source = source.replaceAll("[\\\\/]+", "-");
        return source.length() > 180 ? source.substring(source.length() - 180) : source;
    }

    private String readResourceContent(Resource resource) throws IOException {
        byte[] bytes = resource.getInputStream().readAllBytes();
        if (bytes.length > MAX_FILE_BYTES) {
            throw new IllegalArgumentException("Knowledge resource exceeds max size: " + resourceName(resource));
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
