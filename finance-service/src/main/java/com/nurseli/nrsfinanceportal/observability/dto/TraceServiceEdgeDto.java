package com.nurseli.nrsfinanceportal.observability.dto;

public record TraceServiceEdgeDto(String from, String to, double durationMs) {}
