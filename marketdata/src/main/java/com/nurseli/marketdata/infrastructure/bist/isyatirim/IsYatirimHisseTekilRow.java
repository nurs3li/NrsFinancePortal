package com.nurseli.marketdata.infrastructure.bist.isyatirim;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * İş Yatırım {@code Data.aspx/HisseTekil} JSON array elemanı — yalnızca deserialization için.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IsYatirimHisseTekilRow(
        @JsonProperty("HGDG_HS_KODU") String hgdgHsKodu,
        @JsonProperty("HGDG_TARIH") String hgdgTarih,
        @JsonProperty("HGDG_KAPANIS") BigDecimal hgdgKapanis,
        @JsonProperty("HGDG_AOF") BigDecimal hgdgAof,
        @JsonProperty("HGDG_MIN") BigDecimal hgdgMin,
        @JsonProperty("HGDG_MAX") BigDecimal hgdgMax,
        @JsonProperty("HGDG_HACIM") BigDecimal hgdgHacim,
        @JsonProperty("END_ENDEKS_KODU") String endEndeksKodu,
        @JsonProperty("END_TARIH") Long endTarih,
        @JsonProperty("END_SEANS") Integer endSeans,
        @JsonProperty("END_DEGER") BigDecimal endDeger,
        @JsonProperty("DD_DOVIZ_KODU") String ddDovizKodu,
        @JsonProperty("DD_DT_KODU") String ddDtKodu,
        @JsonProperty("DD_TARIH") Long ddTarih,
        @JsonProperty("DD_DEGER") BigDecimal ddDeger,
        @JsonProperty("DOLAR_BAZLI_FIYAT") BigDecimal dolarBazliFiyat,
        @JsonProperty("ENDEKS_BAZLI_FIYAT") BigDecimal endeksBazliFiyat,
        @JsonProperty("DOLAR_HACIM") BigDecimal dolarHacim,
        @JsonProperty("SERMAYE") BigDecimal sermaye,
        @JsonProperty("HG_KAPANIS") BigDecimal hgKapanis,
        @JsonProperty("HG_AOF") BigDecimal hgAof,
        @JsonProperty("HG_MIN") BigDecimal hgMin,
        @JsonProperty("HG_MAX") BigDecimal hgMax,
        @JsonProperty("PD") BigDecimal pd,
        @JsonProperty("PD_USD") BigDecimal pdUsd,
        @JsonProperty("HAO_PD") BigDecimal haoPd,
        @JsonProperty("HAO_PD_USD") BigDecimal haoPdUsd,
        @JsonProperty("HG_HACIM") BigDecimal hgHacim,
        @JsonProperty("DOLAR_BAZLI_MIN") BigDecimal dolarBazliMin,
        @JsonProperty("DOLAR_BAZLI_MAX") BigDecimal dolarBazliMax,
        @JsonProperty("DOLAR_BAZLI_AOF") BigDecimal dolarBazliAof
) {}
