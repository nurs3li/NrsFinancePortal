package com.nurseli.marketdata.application;

import com.nurseli.marketdata.config.ViopHybridProperties;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class ViopCalculationService {
    private final ViopHybridProperties properties;

    public ViopCalculationService(ViopHybridProperties properties) {
        this.properties = properties;
    }

    public BigDecimal calculateBasis(BigDecimal futuresPrice, BigDecimal spotPrice) {
        if (futuresPrice == null || spotPrice == null) {
            return BigDecimal.ZERO;
        }
        return futuresPrice.subtract(spotPrice);
    }

    public BigDecimal basisPercent(BigDecimal basis, BigDecimal spotPrice) {
        if (basis == null || spotPrice == null || spotPrice.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return basis.divide(spotPrice, 6, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
    }

    public BigDecimal annualizedBasisPct(BigDecimal basis, BigDecimal spotPrice) {
        if (basis == null || spotPrice == null || spotPrice.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return basis.divide(spotPrice, 6, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("36500"))
                .divide(new BigDecimal("30"), 4, RoundingMode.HALF_UP);
    }

    public BigDecimal maintenanceMargin(BigDecimal futuresPrice) {
        if (futuresPrice == null) {
            return BigDecimal.ZERO;
        }
        return futuresPrice.multiply(new BigDecimal("0.12")).setScale(4, RoundingMode.HALF_UP);
    }

    public String qualityFlag(BigDecimal basisPct) {
        double limit = Math.max(0.0, properties.getSuspiciousBasisPctLimit());
        if (basisPct == null) {
            return "FALLBACK";
        }
        return basisPct.abs().compareTo(BigDecimal.valueOf(limit)) > 0 ? "SUSPICIOUS" : "EXACT";
    }
}

