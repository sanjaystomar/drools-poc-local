package com.poc.drools.repository;

import com.poc.drools.entity.DroolsRule;
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
