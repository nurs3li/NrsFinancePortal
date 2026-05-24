package com.nurseli.nrsfinanceportal.infrastructure.observability.dto;

/**
 * Trace servis düğümü DTO.
 */
public record TraceServiceNodeDto(String id, String label, double durationMs) {}
