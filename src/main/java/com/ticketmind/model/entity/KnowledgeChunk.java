package com.ticketmind.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "knowledge_chunks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeChunk {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 180)
    private String source;

    @Column(nullable = false, columnDefinition = "integer default 0")
    private int sourceIndex;

    @Lob
    @Column(nullable = false)
    private String content;

    @Lob
    private String embeddingJson;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
