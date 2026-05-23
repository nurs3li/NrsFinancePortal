package com.nurseli.nrsfinanceportal.infrastructure.observability.dto;

/**
 * Audit log liste satırı DTO.
 */
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
