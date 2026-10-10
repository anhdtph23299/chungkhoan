package com.vntrade.backend.service.marketdata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vntrade.backend.dto.StockQuote;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StockPriceServiceTest {

    @Mock
    private RestTemplate restTemplate;

    private ObjectMapper objectMapper;
    private StockPriceService stockPriceService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        stockPriceService = new StockPriceService(restTemplate, objectMapper);
    }

    @Test
    @DisplayName("Khi API VNDirect trả về nến sàn thật: Quote phải có cờ REAL và giá chuẩn xác")
    void getQuote_whenVndirectReturnsData_shouldReturnRealQuote() {
        String vndirectJson = """
            {
                "data": [
                    {
                        "code": "FPT",
                        "close": 135000.0,
                        "change": 1500.0,
                        "pctChange": 1.12,
                        "open": 133500.0,
                        "high": 136000.0,
                        "low": 133000.0,
                        "nmVolume": 3500000,
                        "exchange": "HOSE"
                    }
                ]
            }
            """;

        when(restTemplate.getForObject(contains("finfo-api.vndirect.com.vn"), eq(String.class)))
            .thenReturn(vndirectJson);

        StockQuote quote = stockPriceService.getQuote("FPT");

        assertNotNull(quote);
        assertEquals("FPT", quote.getSymbol());
        assertEquals(BigDecimal.valueOf(135000.0), quote.getPrice());
        assertEquals("REAL", quote.getDataSource());
        assertTrue(quote.isReal());
        assertFalse(quote.isStale());
        assertEquals("VNDIRECT", quote.getSource());
    }

    @Test
    @DisplayName("Khi toàn bộ API sàn mất kết nối và không có cache: Phải trả về STALE, giá null, TUYỆT ĐỐI KHÔNG dùng giá giả")
    void getQuote_whenAllApisFailAndNoCache_shouldReturnStaleDisconnectedQuoteWithNullPrice() {
        // Giả lập mất mạng hoàn toàn (rút cáp mạng / timeout)
        when(restTemplate.getForObject(anyString(), eq(String.class)))
            .thenThrow(new RestClientException("Connection refused / Network unreachable"));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(String.class)))
            .thenThrow(new RestClientException("Connection timed out"));

        StockQuote quote = stockPriceService.getQuote("HPG");

        assertNotNull(quote);
        assertEquals("HPG", quote.getSymbol());
        assertNull(quote.getPrice(), "Khi mất mạng và không có cache, giá bắt buộc phải là null để ngăn chặn bot đặt lệnh sai lệch");
        assertEquals("STALE", quote.getDataSource());
        assertTrue(quote.isStale());
        assertFalse(quote.isReal());
        assertEquals("DISCONNECTED", quote.getSource());
    }

    @Test
    @DisplayName("Khi toàn bộ API sàn mất kết nối nhưng có cache cũ: Trả về giá cũ nhưng gắn cờ STALE để bot dừng mở lệnh")
    void getQuote_whenAllApisFailButCacheExists_shouldReturnStaleQuoteFromCache() {
        // Bước 1: Lấy thành công lần đầu để nạp vào cache
        String vndirectJson = """
            {
                "data": [
                    {
                        "code": "MWG",
                        "close": 68000.0,
                        "change": 500.0,
                        "pctChange": 0.74,
                        "open": 67500.0,
                        "high": 68500.0,
                        "low": 67200.0,
                        "nmVolume": 2100000,
                        "exchange": "HOSE"
                    }
                ]
            }
            """;

        when(restTemplate.getForObject(contains("finfo-api.vndirect.com.vn"), eq(String.class)))
            .thenReturn(vndirectJson);

        StockQuote realQuote = stockPriceService.getQuote("MWG");
        assertNotNull(realQuote);
        assertEquals("REAL", realQuote.getDataSource());

        // Bước 2: Giả lập rớt mạng ở lần quét sau khi cache hết hạn
        stockPriceService.invalidateCache("MWG");
        reset(restTemplate);
        when(restTemplate.getForObject(anyString(), eq(String.class)))
            .thenThrow(new RestClientException("Network dropped"));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(String.class)))
            .thenThrow(new RestClientException("Network dropped"));

        StockQuote staleQuote = stockPriceService.getQuote("MWG");

        assertNotNull(staleQuote);
        assertEquals(BigDecimal.valueOf(68000.0), staleQuote.getPrice());
        assertEquals("STALE", staleQuote.getDataSource(), "Khi không kết nối được sàn, dữ liệu phải bị hạ cấp thành STALE");
        assertTrue(staleQuote.isStale());
        assertTrue(staleQuote.getSource().contains("STALE"));
    }

    @Test
    @DisplayName("Kiểm tra trạng thái kết nối nguồn dữ liệu thị trường (isMarketDataConnected)")
    void isMarketDataConnected_test() {
        // Case 1: Mất kết nối
        when(restTemplate.getForObject(anyString(), eq(String.class)))
            .thenThrow(new RestClientException("No Internet"));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(String.class)))
            .thenThrow(new RestClientException("No Internet"));

        assertFalse(stockPriceService.isMarketDataConnected(), "Khi không gọi được API sàn, isMarketDataConnected phải trả về false");
    }
}
