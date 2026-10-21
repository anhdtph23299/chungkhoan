package com.vntrade.backend.controller;

import com.vntrade.backend.entity.SignalValidationTrade;
import com.vntrade.backend.service.execution.SignalValidatorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/validator")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:4200", "http://127.0.0.1:4200"})
public class SignalValidatorController {

    private final SignalValidatorService validatorService;

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(validatorService.getStats());
    }

    @GetMapping("/signals")
    public ResponseEntity<List<SignalValidationTrade>> getSignals() {
        return ResponseEntity.ok(validatorService.getRecentSignals());
    }

    @GetMapping("/logs")
    public ResponseEntity<List<String>> getLogs() {
        return ResponseEntity.ok(validatorService.getValidatorLogs());
    }

    @PostMapping("/toggle")
    public ResponseEntity<Map<String, Object>> toggle() {
        boolean newState = !validatorService.isRunning();
        validatorService.setRunning(newState);
        return ResponseEntity.ok(Map.of(
            "running", newState,
            "message", newState ? "Đã bật Job 2 Signal Validator" : "Đã tạm dừng Job 2 Signal Validator"
        ));
    }

    @DeleteMapping("/reset")
    public ResponseEntity<Map<String, Object>> reset() {
        validatorService.resetAll();
        return ResponseEntity.ok(Map.of(
            "status", "RESET_OK",
            "message", "Đã xóa toàn bộ dữ liệu lịch sử kiểm chứng tín hiệu mở toang cánh cửa"
        ));
    }
}
