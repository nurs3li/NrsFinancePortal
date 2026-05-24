package com.nurseli.nrsfinanceportal.infrastructure.observability.dto;

/**
 * Trace servisler arası kenar DTO.
 */
public record TraceServiceEdgeDto(String from, String to, double durationMs) {}
