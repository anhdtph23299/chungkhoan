package com.vntrade.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MultiFactorAttributionDto {
    private String symbol;
    private String assetName;
    private BigDecimal jensensAlphaPercent;           // Alpha thuần túy sinh ra bởi bot (%/năm)
    private BigDecimal marketBetaExposure;            // Hệ số nhạy cảm thị trường Beta (MKT)
    private BigDecimal sizeFactorSmb;                 // Nhân tố Quy mô (SMB: Small Minus Big)
    private BigDecimal valueFactorHml;                // Nhân tố Giá trị (HML: High Minus Low Book-to-Price)
    private BigDecimal momentumFactorWml;             // Nhân tố Đà giá (WML: Carhart Momentum 12M)
    private BigDecimal qualityFactorRmw;              // Nhân tố Chất lượng (RMW: Robust Minus Weak ROE)
    private BigDecimal rSquaredPercent;               // Độ chuẩn xác mô hình đa nhân tố R^2 (%)
    private String primaryDriverFactor;               // Nhân tố động lực chính chi phối giá cổ phiếu
    private List<String> factorStrengths;             // Phân tích độ mạnh yếu từng nhân tố
    private String institutionalFactorVerdict;        // Nhận định phân bổ nhân tố của quỹ
}
