package com.nurseli.marketdata.infrastructure.tefas;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

public final class TefasApiModels {

    private TefasApiModels() {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TefasEnvelope<T>(String errorCode, String errorMessage, List<T> resultList) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TefasReturnRow(
            String fonKodu,
            String fonUnvan,
            String fonTurAciklama,
            Boolean tefasDurum,
            Double getiri1a,
            Double getiri3a,
            Double getiri6a,
            Double getiri1y,
            Double getiriyb,
            Double getiri3y,
            Double getiri5y,
            String riskDegeri) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TefasPriceRow(String fonKodu, String fonUnvan, String tarih, Double fiyat) {}
}
