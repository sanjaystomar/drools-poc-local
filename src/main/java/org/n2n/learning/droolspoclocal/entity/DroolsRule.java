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
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DroolsRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Logical name / identifier for this rule set (e.g. "loan-approval").
     * Must be unique.
     */
    @Column(name = "rule_name", nullable = false, unique = true, length = 100)
    private String ruleName;

    /**
     * Human-readable description of what this rule set does.
     */
    @Column(name = "description", length = 500)
    private String description;

    /**
     * The full DRL source content for this rule set.
     */
    @Lob
    @Column(name = "drl_content", nullable = false, columnDefinition = "TEXT")
    private String drlContent;

    /**
     * Whether this rule set should be loaded into the KieContainer.
     * Inactive rules are ignored at startup and on reload.
     */
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
