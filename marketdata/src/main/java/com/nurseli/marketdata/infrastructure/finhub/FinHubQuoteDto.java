package com.nurseli.marketdata.infrastructure.finhub;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class FinHubQuoteDto {
    @JsonProperty("c")
    private Double c;  // current price
    @JsonProperty("o")
    private Double o;  // open
    @JsonProperty("h")
    private Double h;  // high
    @JsonProperty("l")
    private Double l;  // low
    @JsonProperty("pc")
    private Double pc; // previous close
    @JsonProperty("t")
    private Long t;    // timestamp (epoch s)
}