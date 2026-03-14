package org.n2n.learning.droolspoclocal.service;

import org.kie.api.runtime.KieContainer;
import org.n2n.learning.droolspoclocal.config.DroolsConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Manages the lifecycle of the KieContainer and supports hot-reloading rules
 * from the database without restarting the application.
 *
 * All rule execution goes through this service to get the current container.
 */
@Service
public class RuleReloadService {

    private static final Logger log = LoggerFactory.getLogger(RuleReloadService.class);

    private final DroolsConfig droolsConfig;
    private final AtomicReference<KieContainer> containerRef;
    private volatile LocalDateTime lastReloadedAt;

    public RuleReloadService(DroolsConfig droolsConfig, KieContainer initialContainer) {
        this.droolsConfig = droolsConfig;
        this.containerRef = new AtomicReference<>(initialContainer);
        this.lastReloadedAt = LocalDateTime.now();
    }

    /**
     * Returns the currently active KieContainer.
     */
    public KieContainer getKieContainer() {
        return containerRef.get();
    }

    /**
     * Rebuilds the KieContainer from the current state of the database.
     * Thread-safe: uses AtomicReference to swap the container atomically.
     *
     * @return reload result summary
     */
    public ReloadResult reload() {
        log.info("Hot-reloading Drools rules from database...");
        try {
            KieContainer newContainer = droolsConfig.buildKieContainer();
            KieContainer old = containerRef.getAndSet(newContainer);

            // Give in-flight sessions a moment before disposing the old container
            if (old != null) {
                try { old.dispose(); } catch (Exception ex) {
                    log.warn("Error disposing old KieContainer: {}", ex.getMessage());
                }
            }

            lastReloadedAt = LocalDateTime.now();
            log.info("Rules hot-reloaded successfully at {}", lastReloadedAt);
            return ReloadResult.success(lastReloadedAt);

        } catch (Exception ex) {
            log.error("Rule reload failed: {}", ex.getMessage(), ex);
            return ReloadResult.failure(ex.getMessage());
        }
    }

    public LocalDateTime getLastReloadedAt() {
        return lastReloadedAt;
    }

    // ── Inner result DTO ─────────────────────────────────────────────────────

    public record ReloadResult(boolean success, String message, LocalDateTime reloadedAt) {
        static ReloadResult success(LocalDateTime at) {
            return new ReloadResult(true, "Rules reloaded successfully", at);
        }
        static ReloadResult failure(String error) {
            return new ReloadResult(false, "Reload failed: " + error, null);
        }
    }
}
