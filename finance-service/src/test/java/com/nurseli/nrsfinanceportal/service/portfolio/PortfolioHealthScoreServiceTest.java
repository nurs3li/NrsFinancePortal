package com.nurseli.nrsfinanceportal.service.portfolio;

import com.nurseli.nrsfinanceportal.common.dto.PortfolioConcentrationRiskDto;
import com.nurseli.nrsfinanceportal.common.dto.PortfolioHealthScoreDto;
import com.nurseli.nrsfinanceportal.domain.asset.AssetType;
import com.nurseli.nrsfinanceportal.domain.portfolio.ManualPortfolioPosition;
import com.nurseli.nrsfinanceportal.domain.portfolio.ManualPositionStatus;
import com.nurseli.nrsfinanceportal.domain.portfolio.ManualPriceSource;
import com.nurseli.nrsfinanceportal.domain.user.User;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PortfolioHealthScoreServiceTest {

    private final PortfolioHealthScoreService service = new PortfolioHealthScoreService();

    @Test
    void weakWhenManyPenalties() {
        var real = new ManualPortfolioRealReturnCalculator.PortfolioRealReturnResult(
                new BigDecimal("100"),
                BigDecimal.ZERO,
                new BigDecimal("100"),
                new BigDecimal("100"),
                new BigDecimal("-10"),
                null,
                new BigDecimal("110"),
                new BigDecimal("-20"),
                new BigDecimal("-18"),
                true,
                null,
                List.of()
        );
        var concentration = new PortfolioConcentrationRiskDto(
                "BTC", new BigDecimal("65"), new BigDecimal("80"), "HIGH", "msg");
        List<ManualPortfolioPosition> positions = List.of(openPos());

        PortfolioHealthScoreDto health = service.evaluate(
                real, concentration, positions, Map.of(AssetType.CRYPTO, new BigDecimal("100")));

        assertThat(health.score()).isLessThan(60);
        assertThat(health.level()).isEqualTo("WEAK");
    }

    @Test
    void goodWhenFewPenalties() {
        var real = new ManualPortfolioRealReturnCalculator.PortfolioRealReturnResult(
                new BigDecimal("100"),
                BigDecimal.ZERO,
                new BigDecimal("100"),
                new BigDecimal("100"),
                new BigDecimal("10"),
                new BigDecimal("10"),
                new BigDecimal("90"),
                new BigDecimal("10"),
                new BigDecimal("11"),
                true,
                null,
                List.of()
        );
        var concentration = new PortfolioConcentrationRiskDto(
                "A", new BigDecimal("30"), new BigDecimal("60"), "LOW", "ok");
        List<ManualPortfolioPosition> positions = List.of(openPos(), openPos());

        PortfolioHealthScoreDto health = service.evaluate(
                real, concentration, positions,
                Map.of(AssetType.CRYPTO, new BigDecimal("50"), AssetType.FX, new BigDecimal("50")));

        assertThat(health.score()).isGreaterThanOrEqualTo(80);
        assertThat(health.level()).isEqualTo("GOOD");
    }

    private static ManualPortfolioPosition openPos() {
        User u = User.createFromIdentity("kc", "e@e.com", "u");
        return ManualPortfolioPosition.createNew(
                u, AssetType.CRYPTO, "BTC", BigDecimal.ONE,
                LocalDate.of(2024, 1, 1), BigDecimal.TEN,
                ManualPriceSource.USER_INPUT, LocalDate.of(2024, 1, 1), true,
                BigDecimal.ZERO,
                ManualPositionStatus.OPEN,
                null, null, null, null, false, null, null
        );
    }
}
