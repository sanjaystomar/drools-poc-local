package org.n2n.learning.droolspoclocal.service;

import lombok.extern.slf4j.Slf4j;
import org.kie.api.runtime.KieSession;
import org.n2n.learning.droolspoclocal.listener.RuleAuditListener;
import org.n2n.learning.droolspoclocal.model.LoanApplication;
import org.n2n.learning.droolspoclocal.model.Order;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

@Service
@Slf4j
public class ModularDroolService {
    private final RuleReloadService reloadService;
    private final RuleAuditListener auditListener;

    public ModularDroolService (RuleReloadService reloadService,
                         RuleAuditListener auditListener) {
        this.reloadService = reloadService;
        this.auditListener = auditListener;
    }

    public LoanApplication evaluateLoan(LoanApplication app) {
        // "loanSession" — named session for the loan KieBase only
        // loan rules are isolated; discount/fraud rules never fire here
        return executeStateful("loanSession", app, session -> {
            session.insert(app);
            session.fireAllRules();
        });
    }

    public Order applyDiscount(Order order) {
        // "discountSession" — isolated to discount rules only
        return executeStateful("discountSession", order, session -> {
            session.insert(order);
            session.fireAllRules();
        });
    }

    // ── Generic helpers ──────────────────────────────────────────────────────

    private <T> T executeStateful(String sessionName, T fact,
                                  Consumer<KieSession> execution) {
        KieSession session = reloadService.getKieContainer()
                .newKieSession(sessionName);
        session.addEventListener(auditListener);
        try {
            execution.accept(session);
        } finally {
            session.dispose();
        }
        return fact;
    }
}
