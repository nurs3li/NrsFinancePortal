package com.nurseli.marketdata.infrastructure.finhub;

import lombok.Data;

import java.util.List;

@Data
public class FinHubCandleDto {
    /**
     * Finnhub candle status ("ok" or "no_data").
     */
    private String s;
    private List<Double> c;
    private List<Double> h;
    private List<Double> l;
    private List<Double> o;
    private List<Long> t;
    private List<Double> v;
}
