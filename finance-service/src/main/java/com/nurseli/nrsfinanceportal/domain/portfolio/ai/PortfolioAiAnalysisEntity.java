package com.nurseli.nrsfinanceportal.domain.portfolio.ai;

import com.nurseli.nrsfinanceportal.domain.user.User;
import jakarta.persistence.*;

import java.time.Instant;

/**
 * Portföy AI analiz sonucu entity; analiz tipi, risk profili ve öneri metinlerini persist eder.
 */
@Entity
@Table(name = "portfolio_ai_analysis")
public class PortfolioAiAnalysisEntity {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "title", length = 120)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "analysis_type", nullable = false, length = 32)
    private PortfolioAiAnalysisType analysisType;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_profile", length = 16)
    private PortfolioAiRiskProfile riskProfile;

    @Enumerated(EnumType.STRING)
    @Column(name = "detail_level", length = 16)
    private PortfolioAiDetailLevel detailLevel;

    @Column(name = "portfolio_snapshot_json", nullable = false, columnDefinition = "TEXT")
    private String portfolioSnapshotJson;

    @Column(name = "ai_output_json", nullable = false, columnDefinition = "TEXT")
    private String aiOutputJson;

    @Column(name = "portfolio_score")
    private Integer portfolioScore;

    @Column(name = "risk_score")
    private Integer riskScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "confidence", length = 16)
    private PortfolioAiConfidenceLevel confidence;

    @Enumerated(EnumType.STRING)
    @Column(name = "concentration_risk", length = 16)
    private PortfolioAiConcentrationLevel concentrationRisk;

    @Column(name = "request_hash", length = 64)
    private String requestHash;

    @Column(name = "model", length = 64)
    private String model;

    @Column(name = "prompt_version", length = 32)
    private String promptVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PortfolioAiAnalysisEntity() {
    }

    public PortfolioAiAnalysisEntity(
            String id,
            User user,
            String title,
            PortfolioAiAnalysisType analysisType,
            PortfolioAiRiskProfile riskProfile,
            PortfolioAiDetailLevel detailLevel,
            String portfolioSnapshotJson,
            String aiOutputJson,
            Integer portfolioScore,
            Integer riskScore,
            PortfolioAiConfidenceLevel confidence,
            PortfolioAiConcentrationLevel concentrationRisk,
            String requestHash,
            String model,
            String promptVersion,
            Instant createdAt
    ) {
        this.id = id;
        this.user = user;
        this.title = title;
        this.analysisType = analysisType;
        this.riskProfile = riskProfile;
        this.detailLevel = detailLevel;
        this.portfolioSnapshotJson = portfolioSnapshotJson;
        this.aiOutputJson = aiOutputJson;
        this.portfolioScore = portfolioScore;
        this.riskScore = riskScore;
        this.confidence = confidence;
        this.concentrationRisk = concentrationRisk;
        this.requestHash = requestHash;
        this.model = model;
        this.promptVersion = promptVersion;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getTitle() {
        return title;
    }

    public PortfolioAiAnalysisType getAnalysisType() {
        return analysisType;
    }

    public PortfolioAiRiskProfile getRiskProfile() {
        return riskProfile;
    }

    public PortfolioAiDetailLevel getDetailLevel() {
        return detailLevel;
    }

    public String getPortfolioSnapshotJson() {
        return portfolioSnapshotJson;
    }

    public String getAiOutputJson() {
        return aiOutputJson;
    }

    public Integer getPortfolioScore() {
        return portfolioScore;
    }

    public Integer getRiskScore() {
        return riskScore;
    }

    public PortfolioAiConfidenceLevel getConfidence() {
        return confidence;
    }

    public PortfolioAiConcentrationLevel getConcentrationRisk() {
        return concentrationRisk;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public String getModel() {
        return model;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
