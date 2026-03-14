package org.n2n.learning.droolspoclocal.listener;

import lombok.extern.slf4j.Slf4j;
import org.kie.api.event.rule.AfterMatchFiredEvent;
import org.kie.api.event.rule.BeforeMatchFiredEvent;
import org.kie.api.event.rule.DefaultAgendaEventListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class RuleAuditListener extends DefaultAgendaEventListener {
    @Override
    public void beforeMatchFired(BeforeMatchFiredEvent event) {
        log.debug(">> BEFORE | rule='{}' | facts={}",
                event.getMatch().getRule().getName(),
                event.getMatch().getObjects());
    }

    @Override
    public void afterMatchFired(AfterMatchFiredEvent event) {
        log.info(">> FIRED  | rule='{}'",
                event.getMatch().getRule().getName());
    }
}
