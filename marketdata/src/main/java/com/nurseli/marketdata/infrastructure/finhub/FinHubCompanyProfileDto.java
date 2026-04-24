package com.nurseli.marketdata.infrastructure.finhub;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class FinHubCompanyProfileDto {
    @JsonProperty("ticker")
    private String ticker;

    /**
     * Finnhub profile2: "marketCapitalization" değeri milyon USD cinsindendir.
     */
    @JsonProperty("marketCapitalization")
    private Double marketCapitalization;
}
