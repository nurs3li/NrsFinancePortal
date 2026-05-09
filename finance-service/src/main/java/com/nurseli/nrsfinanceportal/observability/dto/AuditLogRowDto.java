package com.nurseli.nrsfinanceportal.observability.dto;

public record AuditLogRowDto(
        String cursor,
        String timestamp,
        String level,
        String serviceName,
        String message,
        String traceId,
        String spanId,
        String correlationId,
        String logger
) {}
