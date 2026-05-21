package com.nurseli.nrsfinanceportal.service.portfolio.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nurseli.nrsfinanceportal.common.dto.portfolioai.PortfolioAiAnalysisResponse;
import com.nurseli.nrsfinanceportal.common.dto.portfolioai.PortfolioAiAssetCommentDto;
import com.nurseli.nrsfinanceportal.common.dto.portfolioai.PortfolioAiAssetInsightDto;
import com.nurseli.nrsfinanceportal.common.dto.portfolioai.PortfolioAiDecisionPerspectiveDto;
import com.nurseli.nrsfinanceportal.common.dto.portfolioai.PortfolioAiHistoryItemDto;
import com.nurseli.nrsfinanceportal.common.dto.portfolioai.PortfolioAiMacroAndNewsImpactDto;
import com.nurseli.nrsfinanceportal.common.dto.portfolioai.PortfolioAiPortfolioOverviewDto;
import com.nurseli.nrsfinanceportal.domain.portfolio.ai.PortfolioAiAnalysisEntity;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PortfolioAiMapper {

    private final ObjectMapper objectMapper;

    public PortfolioAiMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public PortfolioAiAnalysisResponse toResponse(PortfolioAiAnalysisEntity entity, PortfolioAiParsedOutput output) {
        return new PortfolioAiAnalysisResponse(
                entity.getId(),
                entity.getCreatedAt(),
                resolveTitle(entity),
                entity.getAnalysisType(),
                output.portfolioScore(),
                output.riskScore(),
                output.confidence(),
                output.concentrationRisk(),
                output.summary(),
                output.findings(),
                output.scenarioComment(),
                output.assetComments().stream().map(this::toAssetDto).toList(),
                output.disclaimer(),
                resolveSource(output, entity),
                entity.getModel(),
                toOverviewDto(output.portfolioOverview()),
                toDecisionDto(output.decisionPerspective()),
                toMacroDto(output.macroAndNewsImpact()),
                output.assetInsights() != null
                        ? output.assetInsights().stream().map(this::toInsightDto).toList()
                        : null,
                output.finalNote()
        );
    }

    public PortfolioAiAnalysisResponse toResponse(PortfolioAiAnalysisEntity entity) {
        return toResponse(entity, readOutput(entity));
    }

    public PortfolioAiParsedOutput readOutput(PortfolioAiAnalysisEntity entity) {
        try {
            return objectMapper.readValue(entity.getAiOutputJson(), PortfolioAiParsedOutput.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Stored AI output is invalid", ex);
        }
    }

    public PortfolioAiHistoryItemDto toHistoryItem(PortfolioAiAnalysisEntity entity) {
        PortfolioAiParsedOutput output = readOutput(entity);
        String summary = output.summary() != null && output.summary().length() > 120
                ? output.summary().substring(0, 120)
                : output.summary();
        if ((summary == null || summary.isBlank())
                && output.portfolioOverview() != null
                && output.portfolioOverview().summary() != null) {
            summary = output.portfolioOverview().summary();
            if (summary.length() > 120) {
                summary = summary.substring(0, 120);
            }
        }
        return new PortfolioAiHistoryItemDto(
                entity.getId(),
                entity.getCreatedAt(),
                entity.getAnalysisType(),
                resolveTitle(entity),
                output.portfolioScore(),
                output.riskScore(),
                summary != null ? summary : ""
        );
    }

    private String resolveTitle(PortfolioAiAnalysisEntity entity) {
        if (entity.getTitle() != null && !entity.getTitle().isBlank()) {
            return entity.getTitle().trim();
        }
        return titleFor(entity);
    }

    private String titleFor(PortfolioAiAnalysisEntity entity) {
        return switch (entity.getAnalysisType()) {
            case GENERAL_REVIEW -> "Mevcut portföy AI değerlendirmesi";
            case ONE_WEEK_HOLD -> "1 hafta tutma senaryosu";
            case ONE_MONTH_HOLD -> "1 ay tutma senaryosu";
            case SELL_SCENARIO -> "Tamamını satma senaryosu";
        };
    }

    private String resolveSource(PortfolioAiParsedOutput output, PortfolioAiAnalysisEntity entity) {
        if (output.source() != null && !output.source().isBlank()) {
            return output.source();
        }
        return PortfolioAiModels.sourceFromModel(entity.getModel());
    }

    private PortfolioAiAssetCommentDto toAssetDto(PortfolioAiParsedOutput.PortfolioAiParsedAssetComment a) {
        return new PortfolioAiAssetCommentDto(
                a.symbol(),
                a.assetName(),
                a.assetClass(),
                a.weightPct(),
                a.returnPct(),
                a.assetScore(),
                a.riskScore(),
                a.riskLevel(),
                a.role(),
                a.positiveFactors(),
                a.riskFactors(),
                a.shortComment(),
                a.detailComment()
        );
    }

    private PortfolioAiPortfolioOverviewDto toOverviewDto(PortfolioAiParsedOutput.PortfolioOverviewParsed o) {
        if (o == null) {
            return null;
        }
        return new PortfolioAiPortfolioOverviewDto(
                o.summary(),
                o.currentSituation(),
                o.mainPositive(),
                o.mainRisk()
        );
    }

    private PortfolioAiDecisionPerspectiveDto toDecisionDto(PortfolioAiParsedOutput.DecisionPerspectiveParsed d) {
        if (d == null) {
            return null;
        }
        return new PortfolioAiDecisionPerspectiveDto(
                d.scenario(),
                d.comment(),
                d.shortTermView(),
                d.mediumTermView(),
                d.watchPoints()
        );
    }

    private PortfolioAiMacroAndNewsImpactDto toMacroDto(PortfolioAiParsedOutput.MacroAndNewsImpactParsed m) {
        if (m == null) {
            return null;
        }
        return new PortfolioAiMacroAndNewsImpactDto(
                m.summary(),
                m.dataAvailability(),
                m.relevantItems()
        );
    }

    private PortfolioAiAssetInsightDto toInsightDto(PortfolioAiParsedOutput.AssetInsightParsed a) {
        return new PortfolioAiAssetInsightDto(
                a.symbol(),
                a.assetName(),
                a.assetClass(),
                a.weightPct(),
                a.returnPct(),
                a.role(),
                a.impactOnPortfolio(),
                a.positiveView(),
                a.riskView(),
                a.whatToWatch(),
                a.shortComment(),
                a.detailComment()
        );
    }
}
