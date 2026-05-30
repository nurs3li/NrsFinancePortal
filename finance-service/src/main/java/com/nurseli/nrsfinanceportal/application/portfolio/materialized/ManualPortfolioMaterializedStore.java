package com.nurseli.nrsfinanceportal.application.portfolio.materialized;

import com.nurseli.nrsfinanceportal.api.dto.ManualPortfolioInsightsResponse;
import com.nurseli.nrsfinanceportal.api.dto.ManualPortfolioSummaryView;
import com.nurseli.nrsfinanceportal.api.dto.ManualPortfolioTimeseriesPointDto;
import com.nurseli.nrsfinanceportal.api.dto.ManualPortfolioView;
import com.nurseli.nrsfinanceportal.domain.portfolio.ManualPortfolioReadSnapshot;
import com.nurseli.nrsfinanceportal.domain.portfolio.ManualPortfolioTimeseriesSnapshot;
import com.nurseli.nrsfinanceportal.domain.portfolio.ManualPortfolioWarmStatus;
import com.nurseli.nrsfinanceportal.domain.user.User;
import com.nurseli.nrsfinanceportal.infrastructure.persistence.ManualPortfolioReadSnapshotRepository;
import com.nurseli.nrsfinanceportal.infrastructure.persistence.ManualPortfolioTimeseriesSnapshotRepository;
import com.nurseli.nrsfinanceportal.infrastructure.persistence.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ManualPortfolioMaterializedStore {

    public static final String SERIES_VALUE_6M = "VALUE:6M";
    public static final String SERIES_VALUE_ALL = "VALUE:ALL";

    private final ManualPortfolioReadSnapshotRepository readSnapshotRepository;
    private final ManualPortfolioTimeseriesSnapshotRepository timeseriesSnapshotRepository;
    private final ManualPortfolioMaterializedJsonCodec jsonCodec;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public Optional<ManualPortfolioReadSnapshot> findReadSnapshot(Long userId) {
        return readSnapshotRepository.findById(userId);
    }

    @Transactional
    public ManualPortfolioReadSnapshot markPending(Long userId, String fingerprint) {
        User user = userRepository.findById(userId).orElseThrow();
        ManualPortfolioReadSnapshot row = readSnapshotRepository.findById(userId)
                .orElseGet(() -> ManualPortfolioReadSnapshot.forUser(user));
        row.setPositionsFingerprint(fingerprint);
        row.setWarmStatus(ManualPortfolioWarmStatus.PENDING);
        row.setWarmError(null);
        return readSnapshotRepository.save(row);
    }

    @Transactional
    public void saveReadSnapshot(
            Long userId,
            String fingerprint,
            List<ManualPortfolioView> views,
            ManualPortfolioSummaryView summary,
            ManualPortfolioInsightsResponse insights,
            Instant marketPricingAt
    ) {
        User user = userRepository.findById(userId).orElseThrow();
        ManualPortfolioReadSnapshot row = readSnapshotRepository.findById(userId)
                .orElseGet(() -> ManualPortfolioReadSnapshot.forUser(user));
        row.setPositionsFingerprint(fingerprint);
        row.setViewsJson(jsonCodec.writeViews(views));
        row.setSummaryJson(jsonCodec.writeSummary(summary));
        row.setInsightsJson(insights != null ? jsonCodec.writeInsights(insights) : null);
        row.setMarketPricingAt(marketPricingAt);
        row.setWarmStatus(ManualPortfolioWarmStatus.READY);
        row.setWarmedAt(Instant.now());
        row.setWarmError(null);
        readSnapshotRepository.save(row);
    }

    @Transactional
    public void refreshReadSnapshotPricing(
            Long userId,
            String fingerprint,
            List<ManualPortfolioView> views,
            ManualPortfolioSummaryView summary,
            ManualPortfolioInsightsResponse insights,
            Instant marketPricingAt
    ) {
        User user = userRepository.findById(userId).orElseThrow();
        ManualPortfolioReadSnapshot row = readSnapshotRepository.findById(userId)
                .orElseGet(() -> ManualPortfolioReadSnapshot.forUser(user));
        row.setPositionsFingerprint(fingerprint);
        row.setViewsJson(jsonCodec.writeViews(views));
        row.setSummaryJson(jsonCodec.writeSummary(summary));
        if (insights != null) {
            row.setInsightsJson(jsonCodec.writeInsights(insights));
        }
        row.setMarketPricingAt(marketPricingAt);
        row.setWarmStatus(ManualPortfolioWarmStatus.READY);
        readSnapshotRepository.save(row);
    }

    @Transactional
    public void markFailed(Long userId, String fingerprint, String error) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return;
        }
        ManualPortfolioReadSnapshot row = readSnapshotRepository.findById(userId)
                .orElseGet(() -> ManualPortfolioReadSnapshot.forUser(user));
        row.setPositionsFingerprint(fingerprint);
        row.setWarmStatus(ManualPortfolioWarmStatus.FAILED);
        row.setWarmError(error != null && error.length() > 500 ? error.substring(0, 500) : error);
        readSnapshotRepository.save(row);
    }

    @Transactional
    public void saveTimeseriesSnapshot(
            Long userId,
            String seriesKey,
            LocalDate from,
            LocalDate to,
            String fingerprint,
            List<ManualPortfolioTimeseriesPointDto> points
    ) {
        User user = userRepository.findById(userId).orElseThrow();
        ManualPortfolioTimeseriesSnapshot row = ManualPortfolioTimeseriesSnapshot.of(
                user,
                seriesKey,
                from,
                to,
                jsonCodec.writeTimeseriesPoints(points),
                fingerprint,
                Instant.now()
        );
        timeseriesSnapshotRepository.save(row);
    }

    @Transactional(readOnly = true)
    public Optional<List<ManualPortfolioTimeseriesPointDto>> findTimeseries(
            Long userId,
            String seriesKey,
            String fingerprint
    ) {
        return timeseriesSnapshotRepository.findById(new ManualPortfolioTimeseriesSnapshot.IdKey(userId, seriesKey))
                .filter(row -> fingerprint.equals(row.getPositionsFingerprint()))
                .map(row -> jsonCodec.readTimeseriesPoints(row.getPointsJson()));
    }

    @Transactional
    public void deleteTimeseriesForUser(Long userId) {
        timeseriesSnapshotRepository.deleteByUserId(userId);
    }

    public ReadSnapshotPayload readPayload(ManualPortfolioReadSnapshot row) {
        return new ReadSnapshotPayload(
                jsonCodec.readViews(row.getViewsJson()),
                jsonCodec.readSummary(row.getSummaryJson()),
                jsonCodec.readInsights(row.getInsightsJson()),
                row.getMarketPricingAt(),
                row.getWarmStatus(),
                row.getPositionsFingerprint(),
                row.getWarmedAt()
        );
    }

    public record ReadSnapshotPayload(
            List<ManualPortfolioView> views,
            ManualPortfolioSummaryView summary,
            ManualPortfolioInsightsResponse insights,
            Instant marketPricingAt,
            ManualPortfolioWarmStatus warmStatus,
            String fingerprint,
            Instant warmedAt
    ) {}
}
