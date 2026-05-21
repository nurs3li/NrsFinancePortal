package com.nurseli.marketdata.api.dto;

import java.time.LocalDate;

public record TefasFundHistoryPointDto(LocalDate date, double price) {}
