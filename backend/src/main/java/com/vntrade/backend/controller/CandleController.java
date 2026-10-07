package com.vntrade.backend.controller;

import com.vntrade.backend.dto.Candle;
import com.vntrade.backend.service.CandleDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/candles")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
public class CandleController {

    private final CandleDataService candleDataService;

    @GetMapping("/{symbol}")
    public ResponseEntity<List<Candle>> getCandles(
        @PathVariable String symbol,
        @RequestParam(required = false, defaultValue = "120") int days
    ) {
        return ResponseEntity.ok(candleDataService.getHistoricalCandles(symbol, days));
    }
}
