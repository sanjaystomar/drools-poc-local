package org.n2n.learning.droolspoclocal.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Stores a Drools rule file (.drl content) in the database.
 *
 * Each row represents one logical rule group (e.g. "loan-approval", "discount").
 * The drl_content column holds the full .drl source text.
 */
@Entity
@Table(name = "drools_rules")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class DroolsRule {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rule_name", nullable = false, unique = true, length = 100)
    private String ruleName;

    // e.g. "loan", "discount", "fraud"
    // must match the package declared inside the .drl: "package rules.<category>"
    @Column(name = "category", nullable = false, length = 50)
    private String category;

    @Column(name = "description", length = 500)
    private String description;

    @Lob
    @Column(name = "drl_content", nullable = false, columnDefinition = "TEXT")
    private String drlContent;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}