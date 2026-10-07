package com.vntrade.backend.controller;

import com.vntrade.backend.dto.PortfolioSummary;
import com.vntrade.backend.dto.TradeRequest;
import com.vntrade.backend.entity.Trade;
import com.vntrade.backend.service.TradeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/trades")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
public class TradeController {

    private final TradeService tradeService;
    private final com.vntrade.backend.service.OrderBookService orderBookService;
    private final com.vntrade.backend.service.OrderExecutionAlgorithmService orderExecutionAlgorithmService;
    private final com.vntrade.backend.service.OrderBookImbalanceService orderBookImbalanceService;
    private final com.vntrade.backend.service.ImplementationShortfallAuditService implementationShortfallAuditService;
    private final com.vntrade.backend.service.AlmgrenChrissExecutionService almgrenChrissExecutionService;
    private final com.vntrade.backend.service.IntradayVwapTwapExecutionService intradayVwapTwapExecutionService;
    private final com.vntrade.backend.service.MicrostructureSpoofingDetectorService microstructureSpoofingDetectorService;

    @GetMapping("/spoofing-detector/{symbol}")
    public ResponseEntity<com.vntrade.backend.dto.SpoofingDetectorDto> detectSpoofing(@PathVariable String symbol) {
        return ResponseEntity.ok(microstructureSpoofingDetectorService.detectSpoofing(symbol));
    }

    @GetMapping("/intraday-vwap-twap/{symbol}")
    public ResponseEntity<com.vntrade.backend.dto.IntradayVwapTwapExecutionDto> planIntradayVwapTwap(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "BUY") String side,
            @RequestParam(defaultValue = "10000") int quantity,
            @RequestParam(defaultValue = "VWAP_VOLUME_WEIGHTED") String algorithm) {
        return ResponseEntity.ok(intradayVwapTwapExecutionService.planIntradayExecution(symbol, side, quantity, algorithm));
    }

    @GetMapping("/almgren-chriss/{symbol}")
    public ResponseEntity<com.vntrade.backend.dto.AlmgrenChrissExecutionDto> planAlmgrenChrissExecution(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "10000") int quantity,
            @RequestParam(defaultValue = "0.5") Double riskAversion) {
        return ResponseEntity.ok(almgrenChrissExecutionService.planOptimalExecution(symbol, quantity, riskAversion));
    }

    @GetMapping("/implementation-shortfall/{symbol}")
    public ResponseEntity<com.vntrade.backend.dto.ImplementationShortfallAuditDto> auditImplementationShortfall(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "10000") int quantity,
            @RequestParam(defaultValue = "VWAP_SMART_SLICING") String algorithm) {
        return ResponseEntity.ok(implementationShortfallAuditService.auditImplementationShortfall(symbol, quantity, algorithm));
    }

    @GetMapping("/depth-imbalance/{symbol}")
    public ResponseEntity<com.vntrade.backend.dto.OrderBookImbalanceDto> getDepthImbalance(@PathVariable String symbol) {
        return ResponseEntity.ok(orderBookImbalanceService.analyzeMicrostructure(symbol));
    }

    @GetMapping("/execution-plan/{symbol}")
    public ResponseEntity<com.vntrade.backend.dto.ExecutionAlgoDto> getExecutionPlan(
        @PathVariable String symbol,
        @RequestParam(defaultValue = "5000") int quantity,
        @RequestParam(defaultValue = "VWAP_SMART_SLICING") String algorithm
    ) {
        return ResponseEntity.ok(orderExecutionAlgorithmService.planInstitutionalExecution(symbol, quantity, algorithm));
    }

    @GetMapping("/order-book/{symbol}")
    public ResponseEntity<com.vntrade.backend.dto.OrderBookDto> getOrderBook(@PathVariable String symbol) {
        return ResponseEntity.ok(orderBookService.getOrderBook(symbol));
    }

    @GetMapping
    public List<Trade> getAllTrades() {
        return tradeService.getAllTrades();
    }

    @GetMapping("/{id}")
    public Trade getTradeById(@PathVariable Long id) {
        return tradeService.getTradeById(id);
    }

    @PostMapping
    public Trade createTrade(@RequestBody TradeRequest request) {
        return tradeService.createTrade(request);
    }

    @PutMapping("/{id}")
    public Trade updateTrade(@PathVariable Long id, @RequestBody TradeRequest request) {
        return tradeService.updateTrade(id, request);
    }

    @PostMapping("/{id}/close")
    public Trade closeTrade(@PathVariable Long id, @RequestBody TradeRequest request) {
        return tradeService.closeTrade(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTrade(@PathVariable Long id) {
        tradeService.deleteTrade(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/summary")
    public PortfolioSummary getPortfolioSummary() {
        return tradeService.getPortfolioSummary();
    }
}
