package com.poc.drools.service;

import com.poc.drools.model.LoanApplication;
import com.poc.drools.model.Order;
import org.kie.api.runtime.KieSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DroolsService {

    private static final Logger log = LoggerFactory.getLogger(DroolsService.class);

    private final RuleReloadService reloadService;

    public DroolsService(RuleReloadService reloadService) {
        this.reloadService = reloadService;
    }

    public LoanApplication evaluateLoan(LoanApplication application) {
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
