package com.nurseli.nrsfinanceportal.application.portfolio.materialized;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import com.nurseli.nrsfinanceportal.api.dto.ManualPortfolioInsightsResponse;
import com.nurseli.nrsfinanceportal.api.dto.ManualPortfolioSummaryView;
import com.nurseli.nrsfinanceportal.api.dto.ManualPortfolioTimeseriesPointDto;
import com.nurseli.nrsfinanceportal.api.dto.ManualPortfolioView;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ManualPortfolioMaterializedJsonCodec {

    private final ObjectMapper snapshotMapper;

    public ManualPortfolioMaterializedJsonCodec(ObjectMapper objectMapper) {
        this.snapshotMapper = objectMapper.copy()
                .registerModule(new ParameterNamesModule())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public String writeViews(List<ManualPortfolioView> views) {
        return write(views);
    }

    public List<ManualPortfolioView> readViews(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        return read(json, new TypeReference<>() {});
    }

    public String writeSummary(ManualPortfolioSummaryView summary) {
        return write(summary);
    }

    public ManualPortfolioSummaryView readSummary(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        return read(json, ManualPortfolioSummaryView.class);
    }

    public String writeInsights(ManualPortfolioInsightsResponse insights) {
        return write(insights);
    }

    public ManualPortfolioInsightsResponse readInsights(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        return read(json, ManualPortfolioInsightsResponse.class);
    }

    public String writeTimeseriesPoints(List<ManualPortfolioTimeseriesPointDto> points) {
        return write(points);
    }

    public List<ManualPortfolioTimeseriesPointDto> readTimeseriesPoints(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        return read(json, new TypeReference<>() {});
    }

    private String write(Object value) {
        try {
            return snapshotMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("JSON serialize failed: " + ex.getMessage(), ex);
        }
    }

    private <T> T read(String json, Class<T> type) {
        try {
            return snapshotMapper.readValue(json, type);
        } catch (Exception ex) {
            throw new IllegalStateException("JSON deserialize failed: " + ex.getMessage(), ex);
        }
    }

    private <T> T read(String json, TypeReference<T> type) {
        try {
            return snapshotMapper.readValue(json, type);
        } catch (Exception ex) {
            throw new IllegalStateException("JSON deserialize failed: " + ex.getMessage(), ex);
        }
    }
}
