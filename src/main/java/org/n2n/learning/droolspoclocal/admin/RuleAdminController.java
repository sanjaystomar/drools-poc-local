package org.n2n.learning.droolspoclocal.admin;

import org.n2n.learning.droolspoclocal.entity.DroolsRule;
import org.n2n.learning.droolspoclocal.repository.DroolsRuleRepository;
import org.n2n.learning.droolspoclocal.service.RuleReloadService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * Admin API for managing DRL rules stored in the database.
 *
 * Endpoints:
 *   GET    /api/admin/rules          - list all rules
 *   GET    /api/admin/rules/{id}     - get single rule
 *   POST   /api/admin/rules          - create a new rule
 *   PUT    /api/admin/rules/{id}     - update rule content / metadata
 *   DELETE /api/admin/rules/{id}     - deactivate a rule
 *   POST   /api/admin/rules/reload   - hot-reload KieContainer from DB
 */
@RestController
@RequestMapping("/api/admin/rules")
public class RuleAdminController {

    private final DroolsRuleRepository ruleRepository;
    private final RuleReloadService reloadService;

    public RuleAdminController(DroolsRuleRepository ruleRepository,
                               RuleReloadService reloadService) {
        this.ruleRepository = ruleRepository;
        this.reloadService = reloadService;
    }

    /** List all rules (active and inactive). */
    @GetMapping
    public List<DroolsRule> listAll() {
        return ruleRepository.findAll();
    }

    /** Get a single rule by ID. */
    @GetMapping("/{id}")
    public DroolsRule getById(@PathVariable Long id) {
        return ruleRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Rule not found with id: " + id));
    }

    /**
     * Create a new rule.
     * The rule is NOT automatically loaded — call /reload after creating.
     *
     * Example body:
     * {
     *   "ruleName": "fraud-detection",
     *   "description": "Flags suspicious transactions",
     *   "drlContent": "package com.poc.drools.rules; ...",
     *   "active": true
     * }
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DroolsRule create(@RequestBody DroolsRule rule) {
        if (ruleRepository.findByRuleName(rule.getRuleName()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A rule with name '" + rule.getRuleName() + "' already exists");
        }
        return ruleRepository.save(rule);
    }

    /**
     * Update an existing rule's content, description, or active flag.
     * Call /reload afterwards to apply changes to the running engine.
     */
    @PutMapping("/{id}")
    public DroolsRule update(@PathVariable Long id, @RequestBody DroolsRule updated) {
        DroolsRule existing = ruleRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Rule not found with id: " + id));

        existing.setDescription(updated.getDescription());
        existing.setDrlContent(updated.getDrlContent());
        existing.setActive(updated.isActive());

        return ruleRepository.save(existing);
    }

    /**
     * Soft-delete: marks the rule as inactive.
     * Call /reload to remove it from the running engine.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deactivate(@PathVariable Long id) {
        DroolsRule rule = ruleRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Rule not found with id: " + id));
        rule.setActive(false);
        ruleRepository.save(rule);
        return ResponseEntity.ok(Map.of(
                "message", "Rule '" + rule.getRuleName() + "' deactivated. Call /reload to apply."));
    }

    /**
     * Hot-reload: rebuilds the KieContainer from all active rules in the DB.
     * No restart required.
     *
     * POST /api/admin/rules/reload
     */
    @PostMapping("/reload")
    public ResponseEntity<RuleReloadService.ReloadResult> reload() {
        RuleReloadService.ReloadResult result = reloadService.reload();
        HttpStatus status = result.success() ? HttpStatus.OK : HttpStatus.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(status).body(result);
    }
}
