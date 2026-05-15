package com.nurseli.nrsfinanceportal.common.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class ManualPortfolioAnalysisResponse {

    private final ManualPortfolioView position;
    private final List<Marker> markers;
    private final List<ChartPoint> chartSeries;

    public ManualPortfolioAnalysisResponse(ManualPortfolioView position, List<Marker> markers, List<ChartPoint> chartSeries) {
        this.position = position;
        this.markers = markers;
        this.chartSeries = chartSeries;
    }

    public ManualPortfolioView getPosition() { return position; }
    public List<Marker> getMarkers() { return markers; }
    public List<ChartPoint> getChartSeries() { return chartSeries; }

    public static class Marker {
        private final String type;
        private final LocalDate date;
        private final BigDecimal price;
        private final BigDecimal value;

        public Marker(String type, LocalDate date, BigDecimal price, BigDecimal value) {
            this.type = type;
            this.date = date;
            this.price = price;
            this.value = value;
        }

        public String getType() { return type; }
        public LocalDate getDate() { return date; }
        public BigDecimal getPrice() { return price; }
        public BigDecimal getValue() { return value; }
    }

    public static class ChartPoint {
        private final LocalDate date;
        private final BigDecimal price;
        private final BigDecimal value;

        public ChartPoint(LocalDate date, BigDecimal price, BigDecimal value) {
            this.date = date;
            this.price = price;
            this.value = value;
        }

        public LocalDate getDate() { return date; }
        public BigDecimal getPrice() { return price; }
        public BigDecimal getValue() { return value; }
    }
}
