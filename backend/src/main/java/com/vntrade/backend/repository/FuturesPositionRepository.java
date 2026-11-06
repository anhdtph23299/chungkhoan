package com.vntrade.backend.repository;

import com.vntrade.backend.entity.FuturesPosition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface FuturesPositionRepository extends JpaRepository<FuturesPosition, String> {

    List<FuturesPosition> findByStatus(String status);

    List<FuturesPosition> findByStatusOrderByCloseTimeDesc(String status);

    List<FuturesPosition> findAllByOrderByOpenTimeDesc();

    @Query("SELECT COALESCE(SUM(p.netPnlVnd), 0) FROM FuturesPosition p WHERE p.status = 'CLOSED'")
    BigDecimal getTotalRealizedPnlVnd();
}
