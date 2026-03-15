package org.n2n.learning.droolspoclocal.service;

import lombok.extern.slf4j.Slf4j;
import org.kie.api.command.Command;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.StatelessKieSession;
import org.kie.internal.command.CommandFactory;
import org.n2n.learning.droolspoclocal.listener.RuleAuditListener;
import org.n2n.learning.droolspoclocal.model.LoanApplication;
import org.n2n.learning.droolspoclocal.model.Order;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Service
@Slf4j
public class ModularDroolService {
    private final RuleReloadService reloadService;
    private final RuleAuditListener auditListener;

    public ModularDroolService(RuleReloadService reloadService,
                         RuleAuditListener auditListener) {
        this.reloadService = reloadService;
        this.auditListener = auditListener;
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Loan evaluation — stateful.
     * Uses update() inside rules so stateful working memory is required.
     */
    public LoanApplication evaluateLoanStateful(LoanApplication app) {
        return executeStateful("loanSession", session -> {
            session.insert(app);
            session.fireAllRules();
        }, app);
    }

    /**
     * Loan evaluation — stateless.
     * Rules only set fields on the fact; no update()/retract() needed.
     */
    public LoanApplication evaluateLoanStatless(LoanApplication loanApplication) {
        return executeStateless("loanStatelessSession", loanApplication);
    }

    /**
     * Discount evaluation — stateless.
     * Rules only set fields on the fact; no update()/retract() needed.
     */
    public Order applyDiscount(Order order) {
        return executeStateless("discountStatelessSession", order);
    }

    /**
     * Discount evaluation — stateful variant.
     * Use this if discount rules need to update() facts mid-execution.
     */
    public Order applyDiscountStateful(Order order) {
        return executeStateful("discountSession", session -> {
            session.insert(order);
            session.fireAllRules();
        }, order);
    }

    // ── Stateful executor ────────────────────────────────────────────────────

    /**
     * Opens a named stateful KieSession, runs the given execution block,
     * then disposes the session. Always disposes even on exception.
     *
     * Use when rules call update() or retract() — stateful working memory
     * is required for those operations.
     *
     * @param sessionName  named session registered in KieModuleModel
     * @param execution    lambda that inserts facts and fires rules
     * @param result       the fact object to return after execution
     */
    private <T> T executeStateful(String sessionName,
                                  Consumer<KieSession> execution,
                                  T result) {
        log.debug("Opening stateful session '{}'", sessionName);
        KieSession session = reloadService.getKieContainer()
                .newKieSession(sessionName);
        session.addEventListener(auditListener);
        try {
            execution.accept(session);
            return result;
        } catch (Exception ex) {
            log.error("Error in stateful session '{}': {}", sessionName, ex.getMessage(), ex);
            throw ex;
        } finally {
            session.dispose();
            log.debug("Disposed stateful session '{}'", sessionName);
        }
    }

    // ── Stateless executor ───────────────────────────────────────────────────

    /**
     * Opens a named stateless KieSession and executes against a single fact.
     *
     * Stateless sessions:
     *  - Fire rules exactly once against the provided facts
     *  - Do NOT support update() or retract() inside rules
     *  - Do NOT need dispose() — no working memory is held
     *  - Are slightly faster and simpler for read-only / single-pass rules
     *
     * @param sessionName  named stateless session registered in KieModuleModel
     * @param fact         single fact to evaluate
     */
    private <T> T executeStateless(String sessionName, T fact) {
        log.debug("Opening stateless session '{}'", sessionName);
        StatelessKieSession session = reloadService.getKieContainer()
                .newStatelessKieSession(sessionName);
        session.addEventListener(auditListener);
        try {
            session.execute(fact);                        // single fact
            return fact;
        } catch (Exception ex) {
            log.error("Error in stateless session '{}': {}", sessionName, ex.getMessage(), ex);
            throw ex;
        }
        // No dispose() — stateless sessions have no working memory to release
    }

    /**
     * Stateless execution with multiple facts.
     * Use when rules need to reason across several objects at once.
     *
     * @param sessionName  named stateless session
     * @param facts        collection of facts to insert
     */
    private void executeStatelessMultiFact(String sessionName,
                                           Collection<?> facts) {
        log.debug("Opening stateless session '{}' with {} facts",
                sessionName, facts.size());
        StatelessKieSession session = reloadService.getKieContainer()
                .newStatelessKieSession(sessionName);
        session.addEventListener(auditListener);
        try {
            session.execute(CommandFactory.newInsertElements(facts));
        } catch (Exception ex) {
            log.error("Error in stateless multi-fact session '{}': {}",
                    sessionName, ex.getMessage(), ex);
            throw ex;
        }
    }

    /**
     * Stateless execution with globals + facts.
     * Globals let you pass in services or config objects that rules can read.
     *
     * Example DRL usage:
     *   global com.poc.drools.service.NotificationService notificationService;
     *   then
     *       notificationService.notify("Loan rejected for " + $loan.getApplicantName());
     *
     * @param sessionName  named stateless session
     * @param globals      map of global name → object (must match DRL global declarations)
     * @param facts        facts to insert
     */
    private void executeStatelessWithGlobals(String sessionName,
                                             Map<String, Object> globals,
                                             Collection<?> facts) {
        log.debug("Opening stateless session '{}' with globals={}", sessionName, globals.keySet());
        StatelessKieSession session = reloadService.getKieContainer()
                .newStatelessKieSession(sessionName);
        session.addEventListener(auditListener);

        List<Command<?>> commands = new ArrayList<>();

        // Register globals first
        globals.forEach((name, value) ->
                commands.add(CommandFactory.newSetGlobal(name, value)));

        // Insert all facts
        facts.forEach(fact ->
                commands.add(CommandFactory.newInsert(fact)));

        // Fire
        commands.add(CommandFactory.newFireAllRules());

        try {
            session.execute(CommandFactory.newBatchExecution(commands));
        } catch (Exception ex) {
            log.error("Error in stateless session with globals '{}': {}",
                    sessionName, ex.getMessage(), ex);
            throw ex;
        }
    }
}
