package org.n2n.learning.droolspoclocal.config;

import lombok.extern.slf4j.Slf4j;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.Message;
import org.kie.api.builder.model.KieBaseModel;
import org.kie.api.builder.model.KieModuleModel;
import org.kie.api.builder.model.KieSessionModel;
import org.kie.api.conf.EqualityBehaviorOption;
import org.kie.api.conf.EventProcessingOption;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.conf.ClockTypeOption;
import org.n2n.learning.droolspoclocal.entity.DroolsRule;
import org.n2n.learning.droolspoclocal.repository.DroolsRuleRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Configuration
@Slf4j
public class ModularDroolConfig {

    private static final KieServices kieServices = KieServices.Factory.get();

    private final DroolsRuleRepository ruleRepository;

    public ModularDroolConfig(DroolsRuleRepository ruleRepository) {
        this.ruleRepository = ruleRepository;
    }

    @Bean
    public KieContainer kieContainer() {
        return buildKieContainer();
    }

    public KieContainer buildKieContainer() {
        List<DroolsRule> activeRules = ruleRepository.findAllByActiveTrue();

        // ── 1. Build KieModuleModel (replaces kmodule.xml) ──────────────────
        KieModuleModel moduleModel = kieServices.newKieModuleModel();

        // Group rules by category to create one KieBase per category
        Map<String, List<DroolsRule>> byCategory = activeRules.stream()
                .collect(Collectors.groupingBy(DroolsRule::getCategory));

        for (String category : byCategory.keySet()) {
            String kbaseName = category + "Base";
            String ksessionName = category + "Session";

            KieBaseModel kbaseModel = moduleModel
                    .newKieBaseModel(kbaseName)
                    .setDefault(false)
                    .addPackage("rules." + category)          // must match package in .drl
                    .setEqualsBehavior(EqualityBehaviorOption.EQUALITY)
                    .setEventProcessingMode(EventProcessingOption.CLOUD);

            kbaseModel
                    .newKieSessionModel(ksessionName)
                    .setDefault(false)
                    .setType(KieSessionModel.KieSessionType.STATEFUL)
                    .setClockType(ClockTypeOption.get("realtime"));

            log.info("Registered KieBase='{}' KieSession='{}' for category='{}'",
                    kbaseName, ksessionName, category);
        }

        // ── 2. Write generated kmodule XML + DRL content into KieFileSystem ──
        KieFileSystem kfs = kieServices.newKieFileSystem();
        kfs.writeKModuleXML(moduleModel.toXML());  // in-memory, no file on disk

        for (DroolsRule rule : activeRules) {
            // Virtual path: package folder must match "rules.<category>"
            String path = "src/main/resources/rules/"
                    + rule.getCategory() + "/"
                    + rule.getRuleName() + ".drl";
            log.info("Loading rule '{}' into path '{}'", rule.getRuleName(), path);
            kfs.write(path, rule.getDrlContent());
        }

        // ── 3. Compile and validate ──────────────────────────────────────────
        KieBuilder builder = kieServices.newKieBuilder(kfs);
        builder.buildAll();

        if (builder.getResults().hasMessages(Message.Level.ERROR)) {
            String errors = builder.getResults()
                    .getMessages(Message.Level.ERROR)
                    .stream().map(Message::getText)
                    .collect(Collectors.joining("\n"));
            throw new IllegalStateException("Drools compile errors:\n" + errors);
        }

        KieContainer container = kieServices
                .newKieContainer(builder.getKieModule().getReleaseId());
        log.info("KieContainer built with {} KieBase(s)", byCategory.size());
        return container;
    }
}
