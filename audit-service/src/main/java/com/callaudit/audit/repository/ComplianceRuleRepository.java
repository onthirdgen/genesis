package com.callaudit.audit.repository;

import com.callaudit.audit.model.ComplianceRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ComplianceRuleRepository extends JpaRepository<ComplianceRule, String> {

    List<ComplianceRule> findByIsActive(Boolean isActive);

    List<ComplianceRule> findByCategory(String category);
}
