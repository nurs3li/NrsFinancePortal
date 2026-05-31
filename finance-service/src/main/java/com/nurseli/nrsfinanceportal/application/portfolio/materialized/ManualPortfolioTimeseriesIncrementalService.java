package com.nurseli.nrsfinanceportal.application.portfolio.materialized;

import com.nurseli.nrsfinanceportal.api.dto.ManualPortfolioTimeseriesPointDto;
import com.nurseli.nrsfinanceportal.domain.portfolio.ManualPortfolioPosition;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ManualPortfolioTimeseriesIncrementalService {

  private static final int MERGE_LOOKBACK_DAYS = 14;

  public boolean canIncrementalAdd(
      List<ManualPortfolioTimeseriesPointDto> previous,
      ManualPortfolioPosition added
  ) {
    return previous != null
        && !previous.isEmpty()
        && added != null
        && added.getBuyDate() != null;
  }

  public LocalDate computeMergeFrom(LocalDate oneYearStart, ManualPortfolioPosition added) {
    LocalDate fromBuy = added.getBuyDate().minusDays(MERGE_LOOKBACK_DAYS);
    return oneYearStart.isAfter(fromBuy) ? oneYearStart : fromBuy;
  }

  /**
   * ADD sonrası: mergeFrom öncesi eski noktalar; mergeFrom ve sonrası tarih hizalı toplam (cost + marketValue).
   */
  public List<ManualPortfolioTimeseriesPointDto> mergeAdd(
      List<ManualPortfolioTimeseriesPointDto> previous,
      List<ManualPortfolioTimeseriesPointDto> delta,
      LocalDate mergeFrom
  ) {
    if (previous == null || previous.isEmpty()) {
      return delta != null ? List.copyOf(delta) : List.of();
    }
    if (delta == null || delta.isEmpty()) {
      return List.copyOf(previous);
    }

    Map<LocalDate, ManualPortfolioTimeseriesPointDto> oldByDate = indexByDate(previous);
    List<ManualPortfolioTimeseriesPointDto> prefix = new ArrayList<>();
    for (ManualPortfolioTimeseriesPointDto p : previous) {
      if (p.date() != null && p.date().isBefore(mergeFrom)) {
        prefix.add(p);
      }
    }
    prefix.sort(Comparator.comparing(ManualPortfolioTimeseriesPointDto::date));

    List<ManualPortfolioTimeseriesPointDto> tail = new ArrayList<>();
    for (ManualPortfolioTimeseriesPointDto d : delta) {
      if (d.date() == null || d.date().isBefore(mergeFrom)) {
        continue;
      }
      ManualPortfolioTimeseriesPointDto old = oldByDate.get(d.date());
      tail.add(new ManualPortfolioTimeseriesPointDto(
          d.date(),
          add(old != null ? old.openCostBasisTry() : null, d.openCostBasisTry()),
          addNullableMarket(old != null ? old.marketValueTry() : null, d.marketValueTry())
      ));
    }
    tail.sort(Comparator.comparing(ManualPortfolioTimeseriesPointDto::date));

    List<ManualPortfolioTimeseriesPointDto> merged = new ArrayList<>(prefix.size() + tail.size());
    merged.addAll(prefix);
    merged.addAll(tail);
    return merged;
  }

  private static Map<LocalDate, ManualPortfolioTimeseriesPointDto> indexByDate(
      List<ManualPortfolioTimeseriesPointDto> points
  ) {
    Map<LocalDate, ManualPortfolioTimeseriesPointDto> map = new HashMap<>();
    for (ManualPortfolioTimeseriesPointDto p : points) {
      if (p.date() != null) {
        map.put(p.date(), p);
      }
    }
    return map;
  }

  private static BigDecimal add(BigDecimal a, BigDecimal b) {
    BigDecimal left = a != null ? a : BigDecimal.ZERO;
    BigDecimal right = b != null ? b : BigDecimal.ZERO;
    return left.add(right);
  }

  private static BigDecimal addNullableMarket(BigDecimal a, BigDecimal b) {
    if (a == null && b == null) {
      return null;
    }
    return add(a, b);
  }
}
