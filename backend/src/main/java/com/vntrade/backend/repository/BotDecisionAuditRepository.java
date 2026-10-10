package com.vntrade.backend.repository;

import com.vntrade.backend.entity.BotDecisionAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BotDecisionAuditRepository extends JpaRepository<BotDecisionAudit, Long> {

    List<BotDecisionAudit> findTop50ByOrderByTimestampDesc();

    List<BotDecisionAudit> findBySymbolOrderByTimestampDesc(String symbol);

    List<BotDecisionAudit> findByAuditVerdict(String auditVerdict);

    @Query("SELECT b.rejectedByFilter, COUNT(b) FROM BotDecisionAudit b WHERE b.rejectedByFilter IS NOT NULL GROUP BY b.rejectedByFilter ORDER BY COUNT(b) DESC")
    List<Object[]> countRejectionsByFilter();
}
