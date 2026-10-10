package com.vntrade.backend.service.execution;

import com.vntrade.backend.dto.PortfolioSummary;
import com.vntrade.backend.dto.TradeRequest;
import com.vntrade.backend.entity.Trade;
import com.vntrade.backend.repository.TradeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import com.vntrade.backend.service.decision.QuantitativeStrategyEngine;

@Service
@RequiredArgsConstructor
@Slf4j
public class TradeService {

    private final TradeRepository tradeRepository;

    public List<Trade> getAllTrades() {
        return tradeRepository.findAllByOrderByTradeDateDesc();
    }

    public Trade getTradeById(Long id) {
        return tradeRepository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("Trade không tồn tại: " + id));
    }

    @Transactional
    public Trade createTrade(TradeRequest req) {
        LocalDate date = req.getTradeDate() != null ? req.getTradeDate() : LocalDate.now();
        BigDecimal rawPrice = req.getPrice() != null ? req.getPrice() : BigDecimal.valueOf(10000);
        BigDecimal price = QuantitativeStrategyEngine.roundToVietnameseTick(rawPrice);
        int qty = req.getQuantity() != null ? req.getQuantity() : 100;
        
        // Mặc định phí mua 0.15% nếu không truyền
        BigDecimal fee = req.getFee() != null ? req.getFee() 
            : price.multiply(BigDecimal.valueOf(qty)).multiply(BigDecimal.valueOf(0.0015)).setScale(0, RoundingMode.HALF_UP);

        BigDecimal stopLoss = req.getStopLoss() != null ? QuantitativeStrategyEngine.roundToVietnameseTick(req.getStopLoss()) : null;
        BigDecimal takeProfit = req.getTakeProfit() != null ? QuantitativeStrategyEngine.roundToVietnameseTick(req.getTakeProfit()) : null;

        Trade trade = Trade.builder()
            .symbol(req.getSymbol() != null ? req.getSymbol().toUpperCase().trim() : "STOCK")
            .exchange(req.getExchange() != null ? req.getExchange() : "HOSE")
            .type(req.getType() != null ? req.getType().toLowerCase() : "buy")
            .tradeDate(date)
            .price(price)
            .quantity(qty)
            .fee(fee)
            .strategy(req.getStrategy())
            .stopLoss(stopLoss)
            .takeProfit(takeProfit)
            .sector(req.getSector())
            .reason(req.getReason())
            .notes(req.getNotes())
            .status("open")
            .build();

        // Nếu là lệnh bán, tính PnL ngay
        if ("sell".equalsIgnoreCase(req.getType()) && req.getClosePrice() != null) {
            trade.setCloseDate(req.getCloseDate() != null ? req.getCloseDate() : LocalDate.now());
            trade.setClosePrice(req.getClosePrice());
            trade.setCloseQuantity(req.getCloseQuantity() != null ? req.getCloseQuantity() : qty);
            trade.setStatus("closed");
            calculatePnl(trade);
        }

        return tradeRepository.save(trade);
    }

    @Transactional
    public Trade closeTrade(Long id, TradeRequest req) {
        Trade trade = getTradeById(id);
        trade.setCloseDate(req.getCloseDate() != null ? req.getCloseDate() : LocalDate.now());
        BigDecimal cp = req.getClosePrice() != null ? QuantitativeStrategyEngine.roundToVietnameseTick(req.getClosePrice()) : trade.getPrice();
        trade.setClosePrice(cp);
        trade.setCloseQuantity(req.getCloseQuantity() != null ? req.getCloseQuantity() : trade.getQuantity());
        trade.setStatus("closed");
        if (req.getNotes() != null) trade.setNotes(req.getNotes());
        calculatePnl(trade);
        return tradeRepository.save(trade);
    }

    @Transactional
    public Trade executeMarketClose(Trade trade, BigDecimal closePrice, String reason) {
        trade.setCloseDate(LocalDate.now());
        trade.setClosePrice(QuantitativeStrategyEngine.roundToVietnameseTick(closePrice));
        trade.setCloseQuantity(trade.getQuantity());
        trade.setStatus("closed");
        if (reason != null) trade.setReason(reason);
        calculatePnl(trade);
        return tradeRepository.save(trade);
    }

    @Transactional
    public BigDecimal executePartialClose(Trade trade, BigDecimal rawClosePrice, int quantityToSell, String reason) {
        if (trade == null || trade.getQuantity() <= quantityToSell || quantityToSell < 100) {
            return BigDecimal.ZERO;
        }

        BigDecimal closePrice = QuantitativeStrategyEngine.roundToVietnameseTick(rawClosePrice);

        BigDecimal grossSell = closePrice.multiply(BigDecimal.valueOf(quantityToSell));
        BigDecimal sellFee = grossSell.multiply(BigDecimal.valueOf(0.0015)).setScale(0, RoundingMode.HALF_UP);
        BigDecimal sellTax = grossSell.multiply(BigDecimal.valueOf(0.0010)).setScale(0, RoundingMode.HALF_UP);
        BigDecimal netSell = grossSell.subtract(sellFee).subtract(sellTax);

        BigDecimal costBasis = trade.getPrice().multiply(BigDecimal.valueOf(quantityToSell));
        BigDecimal buyFeePart = (trade.getFee() != null ? trade.getFee() : BigDecimal.ZERO)
            .multiply(BigDecimal.valueOf(quantityToSell))
            .divide(BigDecimal.valueOf(trade.getQuantity()), 0, RoundingMode.HALF_UP);

        BigDecimal partialNetProfit = netSell.subtract(costBasis).subtract(buyFeePart);

        // Cập nhật vị thế còn lại
        trade.setQuantity(trade.getQuantity() - quantityToSell);
        int currentClosed = trade.getPartialClosedQuantity() != null ? trade.getPartialClosedQuantity() : 0;
        trade.setPartialClosedQuantity(currentClosed + quantityToSell);
        trade.setPartialClosePrice(closePrice);
        BigDecimal prevPartialPnl = trade.getPartialRealizedPnl() != null ? trade.getPartialRealizedPnl() : BigDecimal.ZERO;
        trade.setPartialRealizedPnl(prevPartialPnl.add(partialNetProfit));

        // Nâng Stop Loss lên giá hòa vốn (+0.5% phí) làm tròn chuẩn bước giá HOSE/HNX cho số lượng cổ phiếu còn lại
        BigDecimal beStop = QuantitativeStrategyEngine.roundToVietnameseTick(
            trade.getPrice().multiply(BigDecimal.valueOf(1.005))
        );
        if (trade.getStopLoss() == null || beStop.compareTo(trade.getStopLoss()) > 0) {
            trade.setStopLoss(beStop);
        }

        if (reason != null) trade.setNotes((trade.getNotes() != null ? trade.getNotes() + " | " : "") + reason);
        tradeRepository.save(trade);
        log.info("Partial Close: Sold {} shares of {} at {} VND. Realized Profit: {} VND. Remaining: {} shares with SL {}",
            quantityToSell, trade.getSymbol(), closePrice, partialNetProfit, trade.getQuantity(), trade.getStopLoss());
        return partialNetProfit;
    }

    @Transactional
    public Trade updateTrade(Long id, TradeRequest req) {
        Trade trade = getTradeById(id);
        if (req.getSymbol() != null) trade.setSymbol(req.getSymbol().toUpperCase());
        if (req.getPrice() != null) trade.setPrice(req.getPrice());
        if (req.getQuantity() != null) trade.setQuantity(req.getQuantity());
        if (req.getStopLoss() != null) trade.setStopLoss(req.getStopLoss());
        if (req.getTakeProfit() != null) trade.setTakeProfit(req.getTakeProfit());
        if (req.getReason() != null) trade.setReason(req.getReason());
        if (req.getNotes() != null) trade.setNotes(req.getNotes());
        return tradeRepository.save(trade);
    }

    @Transactional
    public void deleteTrade(Long id) {
        tradeRepository.deleteById(id);
    }

    public PortfolioSummary getPortfolioSummary() {
        List<Trade> openTrades = tradeRepository.findOpenBuyTrades();
        List<Trade> allTrades = tradeRepository.findAllByOrderByTradeDateDesc();

        // Tính lãi thực tế từ lệnh đóng (gồm cả phần đóng hết và phần gặt hái từng phần)
        BigDecimal closedPnl = allTrades.stream()
            .filter(t -> "closed".equals(t.getStatus()))
            .map(t -> (t.getPnl() != null ? t.getPnl() : BigDecimal.ZERO)
                .add(t.getPartialRealizedPnl() != null ? t.getPartialRealizedPnl() : BigDecimal.ZERO))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Tính lãi tạm tính từ lệnh mở + phần tiền mặt đã gặt hái từ chốt lời 50%
        BigDecimal openPnl = openTrades.stream()
            .map(t -> (t.getPnl() != null ? t.getPnl() : BigDecimal.ZERO)
                .add(t.getPartialRealizedPnl() != null ? t.getPartialRealizedPnl() : BigDecimal.ZERO))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal baseCapital = BigDecimal.valueOf(100_000_000);
        BigDecimal totalPnl = closedPnl.add(openPnl);
        BigDecimal currentNav = baseCapital.add(totalPnl);

        long closedCount = tradeRepository.countClosedTrades();
        long winCount = tradeRepository.countWinningTrades();
        BigDecimal winRate = closedCount > 0
            ? BigDecimal.valueOf(winCount * 100.0 / closedCount).setScale(1, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        BigDecimal pnlPct = baseCapital.compareTo(BigDecimal.ZERO) > 0
            ? totalPnl.divide(baseCapital, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
            : BigDecimal.ZERO;

        return PortfolioSummary.builder()
            .totalInvested(baseCapital)
            .currentValue(currentNav)
            .totalPnl(totalPnl)
            .totalPnlPercent(pnlPct)
            .openPositions(openTrades.size())
            .totalTrades(closedCount)
            .winningTrades(winCount)
            .winRate(winRate)
            .build();
    }

    private void calculatePnl(Trade trade) {
        if (trade.getClosePrice() == null || trade.getCloseQuantity() == null) return;

        // Phân bổ phí mua tỷ lệ chuẩn xác nếu lệnh đã từng chốt lời từng phần
        int totalOriginalShares = trade.getCloseQuantity() + (trade.getPartialClosedQuantity() != null ? trade.getPartialClosedQuantity() : 0);
        BigDecimal proportionalBuyFee = (trade.getFee() != null && totalOriginalShares > 0)
            ? trade.getFee().multiply(BigDecimal.valueOf(trade.getCloseQuantity())).divide(BigDecimal.valueOf(totalOriginalShares), 0, RoundingMode.HALF_UP)
            : (trade.getFee() != null ? trade.getFee() : BigDecimal.ZERO);

        BigDecimal buyTotal = trade.getPrice()
            .multiply(BigDecimal.valueOf(trade.getCloseQuantity()))
            .add(proportionalBuyFee);

        BigDecimal sellTotal = trade.getClosePrice()
            .multiply(BigDecimal.valueOf(trade.getCloseQuantity()));

        // Phí bán 0.15% & Thuế TNCN bán chứng khoán 0.10% theo quy định thị trường VN
        BigDecimal sellFee = sellTotal.multiply(BigDecimal.valueOf(0.0015)).setScale(0, RoundingMode.HALF_UP);
        BigDecimal sellTax = sellTotal.multiply(BigDecimal.valueOf(0.0010)).setScale(0, RoundingMode.HALF_UP);

        BigDecimal netPnl = sellTotal.subtract(buyTotal).subtract(sellFee).subtract(sellTax);
        BigDecimal pnlPct = buyTotal.compareTo(BigDecimal.ZERO) != 0
            ? netPnl.divide(buyTotal, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
            : BigDecimal.ZERO;

        trade.setPnl(netPnl);
        trade.setPnlPercent(pnlPct);
    }
}