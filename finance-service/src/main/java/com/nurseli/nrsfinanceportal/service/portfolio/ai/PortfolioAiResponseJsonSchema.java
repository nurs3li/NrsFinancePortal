package com.nurseli.nrsfinanceportal.service.portfolio.ai;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI Chat Completions {@code response_format: json_schema} (strict).
 */
public final class PortfolioAiResponseJsonSchema {

    public static final String SCHEMA_NAME = "portfolio_ai_analysis";

    private PortfolioAiResponseJsonSchema() {
    }

    public static Map<String, Object> responseFormat() {
        Map<String, Object> jsonSchema = new LinkedHashMap<>();
        jsonSchema.put("name", SCHEMA_NAME);
        jsonSchema.put("strict", true);
        jsonSchema.put("schema", schema());
        Map<String, Object> format = new LinkedHashMap<>();
        format.put("type", "json_schema");
        format.put("json_schema", jsonSchema);
        return format;
    }

    private static Map<String, Object> schema() {
        Map<String, Object> rootProps = new LinkedHashMap<>();
        rootProps.put("portfolioScore", Map.of("type", "integer"));
        rootProps.put("riskScore", Map.of("type", "integer"));
        rootProps.put("confidence", enumSchema("LOW", "MEDIUM", "HIGH"));
        rootProps.put("concentrationRisk", enumSchema("LOW", "MEDIUM", "HIGH"));
        rootProps.put("summary", Map.of("type", "string"));
        rootProps.put("findings", stringArray());
        rootProps.put("scenarioComment", Map.of("type", "string"));
        rootProps.put("assetComments", Map.of("type", "array", "items", legacyAssetItem()));
        rootProps.put("disclaimer", Map.of("type", "string"));
        rootProps.put("portfolioOverview", overviewObject());
        rootProps.put("decisionPerspective", decisionObject());
        rootProps.put("macroAndNewsImpact", macroNewsObject());
        rootProps.put("assetInsights", Map.of("type", "array", "items", insightAssetItem()));
        rootProps.put("finalNote", Map.of("type", "string"));

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("type", "object");
        root.put("properties", rootProps);
        root.put(
                "required",
                List.of(
                        "portfolioScore",
                        "riskScore",
                        "confidence",
                        "concentrationRisk",
                        "summary",
                        "findings",
                        "scenarioComment",
                        "assetComments",
                        "disclaimer",
                        "portfolioOverview",
                        "decisionPerspective",
                        "macroAndNewsImpact",
                        "assetInsights",
                        "finalNote"
                )
        );
        root.put("additionalProperties", false);
        return root;
    }

    private static Map<String, Object> overviewObject() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("summary", Map.of("type", "string"));
        props.put("currentSituation", Map.of("type", "string"));
        props.put("mainPositive", Map.of("type", "string"));
        props.put("mainRisk", Map.of("type", "string"));
        return objectSchema(props, List.of("summary", "currentSituation", "mainPositive", "mainRisk"));
    }

    private static Map<String, Object> decisionObject() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("scenario", Map.of("type", "string"));
        props.put("comment", Map.of("type", "string"));
        props.put("shortTermView", Map.of("type", "string"));
        props.put("mediumTermView", Map.of("type", "string"));
        props.put("watchPoints", stringArray());
        return objectSchema(
                props,
                List.of("scenario", "comment", "shortTermView", "mediumTermView", "watchPoints")
        );
    }

    private static Map<String, Object> macroNewsObject() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("summary", Map.of("type", "string"));
        props.put("dataAvailability", enumSchema("AVAILABLE", "PARTIAL", "NOT_AVAILABLE"));
        props.put("relevantItems", stringArray());
        return objectSchema(props, List.of("summary", "dataAvailability", "relevantItems"));
    }

    private static Map<String, Object> legacyAssetItem() {
        Map<String, Object> assetProps = new LinkedHashMap<>();
        assetProps.put("symbol", Map.of("type", "string"));
        assetProps.put("assetName", Map.of("type", "string"));
        assetProps.put("assetClass", Map.of("type", "string"));
        assetProps.put("weightPct", Map.of("type", "number"));
        assetProps.put("returnPct", Map.of("type", List.of("number", "null")));
        assetProps.put("assetScore", Map.of("type", "integer"));
        assetProps.put("riskScore", Map.of("type", "integer"));
        assetProps.put("riskLevel", enumSchema("LOW", "MEDIUM", "HIGH"));
        assetProps.put("role", Map.of("type", "string"));
        assetProps.put("positiveFactors", stringArray());
        assetProps.put("riskFactors", stringArray());
        assetProps.put("shortComment", Map.of("type", "string"));
        assetProps.put("detailComment", Map.of("type", "string"));
        return objectSchema(
                assetProps,
                List.of(
                        "symbol",
                        "assetName",
                        "assetClass",
                        "weightPct",
                        "returnPct",
                        "assetScore",
                        "riskScore",
                        "riskLevel",
                        "role",
                        "positiveFactors",
                        "riskFactors",
                        "shortComment",
                        "detailComment"
                )
        );
    }

    private static Map<String, Object> insightAssetItem() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("symbol", Map.of("type", "string"));
        props.put("assetName", Map.of("type", "string"));
        props.put("assetClass", Map.of("type", "string"));
        props.put("weightPct", Map.of("type", "number"));
        props.put("returnPct", Map.of("type", List.of("number", "null")));
        props.put("role", Map.of("type", "string"));
        props.put("impactOnPortfolio", Map.of("type", "string"));
        props.put("positiveView", Map.of("type", "string"));
        props.put("riskView", Map.of("type", "string"));
        props.put("whatToWatch", stringArray());
        props.put("shortComment", Map.of("type", "string"));
        props.put("detailComment", Map.of("type", "string"));
        return objectSchema(
                props,
                List.of(
                        "symbol",
                        "assetName",
                        "assetClass",
                        "weightPct",
                        "returnPct",
                        "role",
                        "impactOnPortfolio",
                        "positiveView",
                        "riskView",
                        "whatToWatch",
                        "shortComment",
                        "detailComment"
                )
        );
    }

    private static Map<String, Object> objectSchema(Map<String, Object> properties, List<String> required) {
        Map<String, Object> obj = new LinkedHashMap<>();
        obj.put("type", "object");
        obj.put("properties", properties);
        obj.put("required", required);
        obj.put("additionalProperties", false);
        return obj;
    }

    private static Map<String, Object> enumSchema(String... values) {
        return Map.of("type", "string", "enum", List.of(values));
    }

    private static Map<String, Object> stringArray() {
        return Map.of("type", "array", "items", Map.of("type", "string"));
    }
}
