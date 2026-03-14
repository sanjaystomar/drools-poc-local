package org.n2n.learning.droolspoclocal.config;

import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.KieModule;
import org.kie.api.builder.Message;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.n2n.learning.droolspoclocal.entity.DroolsRule;
import org.n2n.learning.droolspoclocal.repository.DroolsRuleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import java.util.List;

/**
 * Builds the KieContainer by reading DRL content from the drools_rules table.
 *
 * To hot-reload rules at runtime without restarting, call
 * RuleReloadService.reload() — it rebuilds and replaces the KieContainer.
 */
@Configuration
public class DroolsConfig {

    private static final Logger log = LoggerFactory.getLogger(DroolsConfig.class);
    private static final KieServices kieServices = KieServices.Factory.get();

    private final DroolsRuleRepository ruleRepository;

    public DroolsConfig(DroolsRuleRepository ruleRepository) {
        this.ruleRepository = ruleRepository;
    }

    @Bean
    public KieContainer kieContainer() {
        return buildKieContainer();
    }

    /**
     * Builds a fresh KieContainer from all active rules in the database.
     * Called at startup and whenever rules are reloaded.
     */
    public KieContainer buildKieContainer() {
        List<DroolsRule> activeRules = ruleRepository.findAllByActiveTrue();

        if (activeRules.isEmpty()) {
            log.warn("No active Drools rules found in the database - KieContainer will be empty");
        }

        KieFileSystem kieFileSystem = kieServices.newKieFileSystem();

        for (DroolsRule rule : activeRules) {
            String virtualPath = "src/main/resources/rules/" + rule.getRuleName() + ".drl";
            log.info("Loading rule '{}' from database", rule.getRuleName());
            kieFileSystem.write(virtualPath, rule.getDrlContent());
        }

        KieBuilder kieBuilder = kieServices.newKieBuilder(kieFileSystem);
        kieBuilder.buildAll();

        // Fail fast on compilation errors
        if (kieBuilder.getResults().hasMessages(Message.Level.ERROR)) {
            String errors = kieBuilder.getResults()
                    .getMessages(Message.Level.ERROR)
                    .stream()
                    .map(Message::getText)
                    .reduce("", (a, b) -> a + "\n" + b);
            throw new IllegalStateException("Drools compilation errors:\n" + errors);
        }

        KieModule kieModule = kieBuilder.getKieModule();
        log.info("KieContainer built successfully with {} rule set(s)", activeRules.size());
        return kieServices.newKieContainer(kieModule.getReleaseId());
    }

    /**
     * Returns a new stateful KieSession each time (prototype scope).
     * Always call session.dispose() after use.
     */
    @Bean
    @Scope("prototype")
    public KieSession kieSession() {
        return kieContainer().newKieSession();
    }
}
