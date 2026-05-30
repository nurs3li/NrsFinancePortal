package com.nurseli.nrsfinanceportal.domain.portfolio;

import com.nurseli.nrsfinanceportal.domain.user.User;
import jakarta.persistence.*;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

@Entity
@Table(name = "manual_portfolio_timeseries_snapshot")
@IdClass(ManualPortfolioTimeseriesSnapshot.IdKey.class)
public class ManualPortfolioTimeseriesSnapshot {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Id
    @Column(name = "series_key", length = 32)
    private String seriesKey;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    @Column(name = "from_date", nullable = false)
    private LocalDate fromDate;

    @Column(name = "to_date", nullable = false)
    private LocalDate toDate;

    @Column(name = "points_json", nullable = false, columnDefinition = "TEXT")
    private String pointsJson;

    @Column(name = "positions_fingerprint", nullable = false, length = 64)
    private String positionsFingerprint;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;

    protected ManualPortfolioTimeseriesSnapshot() {
    }

    public static ManualPortfolioTimeseriesSnapshot of(
            User user,
            String seriesKey,
            LocalDate fromDate,
            LocalDate toDate,
            String pointsJson,
            String positionsFingerprint,
            Instant computedAt
    ) {
        ManualPortfolioTimeseriesSnapshot row = new ManualPortfolioTimeseriesSnapshot();
        row.userId = user.getId();
        row.user = user;
        row.seriesKey = seriesKey;
        row.fromDate = fromDate;
        row.toDate = toDate;
        row.pointsJson = pointsJson;
        row.positionsFingerprint = positionsFingerprint;
        row.computedAt = computedAt;
        return row;
    }

    public Long getUserId() {
        return userId;
    }

    public String getSeriesKey() {
        return seriesKey;
    }

    public LocalDate getFromDate() {
        return fromDate;
    }

    public LocalDate getToDate() {
        return toDate;
    }

    public String getPointsJson() {
        return pointsJson;
    }

    public String getPositionsFingerprint() {
        return positionsFingerprint;
    }

    public Instant getComputedAt() {
        return computedAt;
    }

    public void replaceContent(
            LocalDate fromDate,
            LocalDate toDate,
            String pointsJson,
            String positionsFingerprint,
            Instant computedAt
    ) {
        this.fromDate = fromDate;
        this.toDate = toDate;
        this.pointsJson = pointsJson;
        this.positionsFingerprint = positionsFingerprint;
        this.computedAt = computedAt;
    }

    public static final class IdKey implements Serializable {
        private Long userId;
        private String seriesKey;

        public IdKey() {
        }

        public IdKey(Long userId, String seriesKey) {
            this.userId = userId;
            this.seriesKey = seriesKey;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof IdKey idKey)) {
                return false;
            }
            return Objects.equals(userId, idKey.userId) && Objects.equals(seriesKey, idKey.seriesKey);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, seriesKey);
        }
    }
}
