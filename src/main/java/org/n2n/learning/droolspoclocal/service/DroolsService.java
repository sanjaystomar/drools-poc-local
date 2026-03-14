package org.n2n.learning.droolspoclocal.service;

import lombok.extern.slf4j.Slf4j;
import org.kie.api.runtime.KieSession;
import org.n2n.learning.droolspoclocal.model.LoanApplication;
import org.n2n.learning.droolspoclocal.model.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@Profile("direct-impl")
public class DroolsService {

    private final RuleReloadService reloadService;

    public DroolsService(RuleReloadService reloadService) {
        this.reloadService = reloadService;
    }

    public LoanApplication evaluateLoan(LoanApplication application) {
        log.info("HERE ###########");
        KieSession session = reloadService.getKieContainer().newKieSession();
        try {
            log.info("Evaluating loan for applicant: {}", application.getApplicantName());
            session.insert(application);
            int rulesFired = session.fireAllRules();
            log.info("Rules fired: {}", rulesFired);
        } finally {
            session.dispose();
        }
        return application;
    }

    public Order applyDiscount(Order order) {
        KieSession session = reloadService.getKieContainer().newKieSession();
        try {
            log.info("Processing order for customer: {}", order.getCustomerId());
            session.insert(order);
            int rulesFired = session.fireAllRules();
            log.info("Rules fired: {}", rulesFired);
        } finally {
            session.dispose();
        }
        return order;
    }
}
