package com.vntrade.backend.repository;

import com.vntrade.backend.entity.PortfolioSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PortfolioSnapshotRepository extends JpaRepository<PortfolioSnapshot, Long> {
    List<PortfolioSnapshot> findAllByOrderBySnapshotTimeAsc();
    List<PortfolioSnapshot> findTop30ByOrderBySnapshotTimeDesc();
}
