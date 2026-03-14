package org.n2n.learning.droolspoclocal.repository;

import org.n2n.learning.droolspoclocal.entity.DroolsRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DroolsRuleRepository extends JpaRepository<DroolsRule, Long> {

    /** All active rule sets — used when building the KieContainer. */
    List<DroolsRule> findAllByActiveTrue();

    Optional<DroolsRule> findByRuleName(String ruleName);
}
