package com.vntrade.backend.repository;

import com.vntrade.backend.entity.ScheduleEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ScheduleEventRepository extends JpaRepository<ScheduleEvent, Long> {
    List<ScheduleEvent> findByEventDateOrderByCreatedAtAsc(LocalDate date);
    List<ScheduleEvent> findByEventDateBetweenOrderByEventDateAsc(LocalDate start, LocalDate end);
}
