package com.vntrade.backend.controller;

import com.vntrade.backend.entity.ScheduleEvent;
import com.vntrade.backend.repository.ScheduleEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/schedule")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
public class ScheduleController {

    private final ScheduleEventRepository eventRepository;

    @GetMapping
    public List<ScheduleEvent> getAllEvents() {
        return eventRepository.findAll();
    }

    @GetMapping("/date/{date}")
    public List<ScheduleEvent> getEventsByDate(@PathVariable String date) {
        return eventRepository.findByEventDateOrderByCreatedAtAsc(LocalDate.parse(date));
    }

    @GetMapping("/month")
    public List<ScheduleEvent> getEventsByMonth(
        @RequestParam int year,
        @RequestParam int month
    ) {
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
        return eventRepository.findByEventDateBetweenOrderByEventDateAsc(start, end);
    }

    @PostMapping
    public ScheduleEvent createEvent(@RequestBody ScheduleEvent event) {
        return eventRepository.save(event);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEvent(@PathVariable Long id) {
        eventRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
