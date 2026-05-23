package com.nurseli.nrsfinanceportal.infrastructure.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nurseli.nrsfinanceportal.config.ObservabilityProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Audit log ve trace için Grafana Explore deep-link URL üretir; Tempo traceId sorgusu tek yerde toplanır.
 */
@Component
@RequiredArgsConstructor
public class GrafanaExploreUrlFactory {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * traceId için Grafana Explore Tempo deep-link URL üretir.
     */
    public String buildTraceByIdUrl(ObservabilityProperties props, String traceId) {
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("traceId required");
        }
        String base = props.getGrafana().getPublicBaseUrl().trim().replaceAll("/+$", "");
        String orgId = props.getGrafana().getOrgId() == null ? "1" : props.getGrafana().getOrgId().trim();
        String uid = props.getGrafana().getTempoDatasourceUid();

        ObjectNode pane = MAPPER.createObjectNode();
        pane.put("datasource", uid);

        ArrayNode queries = MAPPER.createArrayNode();
        ObjectNode q = MAPPER.createObjectNode();
        q.put("refId", "A");
        ObjectNode ds = MAPPER.createObjectNode();
        ds.put("type", "tempo");
        ds.put("uid", uid);
        q.set("datasource", ds);
        q.put("query", traceId.trim());
        q.put("queryType", "traceId");
        queries.add(q);
        pane.set("queries", queries);

        ObjectNode range = MAPPER.createObjectNode();
        range.put("from", "now-24h");
        range.put("to", "now");
        pane.set("range", range);

        ObjectNode panes = MAPPER.createObjectNode();
        panes.set("t", pane);

        String panesJson;
        try {
            panesJson = MAPPER.writeValueAsString(panes);
        } catch (Exception e) {
            throw new IllegalStateException("panes json", e);
        }
        String encoded = URLEncoder.encode(panesJson, StandardCharsets.UTF_8).replace("+", "%20");
        return base + "/explore?orgId=" + URLEncoder.encode(orgId, StandardCharsets.UTF_8)
                + "&schemaVersion=1&panes=" + encoded;
    }
}
