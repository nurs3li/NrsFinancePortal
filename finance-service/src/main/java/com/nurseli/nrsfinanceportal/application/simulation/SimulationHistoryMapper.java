package com.nurseli.nrsfinanceportal.application.simulation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nurseli.nrsfinanceportal.api.dto.simulation.SimulationHistoryEntryDto;
import com.nurseli.nrsfinanceportal.api.dto.simulation.SimulationHistoryItemDto;
import com.nurseli.nrsfinanceportal.domain.simulation.UserSimulationHistory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Simülasyon geçmişi entity ↔ DTO dönüşümleri.
 */
@Component
public class SimulationHistoryMapper {

    private static final TypeReference<List<SimulationHistoryItemDto>> ITEMS_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper;

    public SimulationHistoryMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public SimulationHistoryEntryDto toSummary(UserSimulationHistory entity) {
        return new SimulationHistoryEntryDto(
                entity.getId(),
                entity.getSavedAt(),
                entity.getLabel(),
                entity.getAmountCurrency(),
                stripSeries(readItems(entity))
        );
    }

    public SimulationHistoryEntryDto toDetail(UserSimulationHistory entity) {
        return new SimulationHistoryEntryDto(
                entity.getId(),
                entity.getSavedAt(),
                entity.getLabel(),
                entity.getAmountCurrency(),
                readItems(entity)
        );
    }

    public String writeItems(List<SimulationHistoryItemDto> items) {
        try {
            return objectMapper.writeValueAsString(items);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Simülasyon sonuçları serileştirilemedi.", ex);
        }
    }

    public List<SimulationHistoryItemDto> readItems(UserSimulationHistory entity) {
        try {
            return objectMapper.readValue(entity.getItemsJson(), ITEMS_TYPE);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Kayıtlı simülasyon verisi okunamadı.", ex);
        }
    }

    private static List<SimulationHistoryItemDto> stripSeries(List<SimulationHistoryItemDto> items) {
        return items.stream()
                .map(item -> new SimulationHistoryItemDto(
                        item.id(),
                        item.assetName(),
                        item.assetType(),
                        item.pickerAssetType(),
                        item.displayCurrency(),
                        item.unitsBought(),
                        item.initialAmount(),
                        item.buyPrice(),
                        item.buyDate(),
                        item.currentPrice(),
                        item.pnl(),
                        item.pnlPct(),
                        item.currentValue(),
                        item.buyPriceSource(),
                        item.historicalPriceDate(),
                        item.qualityFlag(),
                        null,
                        item.visible(),
                        item.message(),
                        item.approximationNoticeCode(),
                        item.scenarioLabel()
                ))
                .toList();
    }
}
