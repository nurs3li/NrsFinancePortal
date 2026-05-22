package com.nurseli.marketdata.controller;

import com.nurseli.marketdata.api.dto.bankfx.BankRatesBoardResponseDto;
import com.nurseli.marketdata.application.bankfx.BankRatesBoardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/market/bank-rates")
@RequiredArgsConstructor
public class BankRatesController {

    private final BankRatesBoardService boardService;

    /**
     * Banka kurları tablosu — yalnızca DB'deki son snapshot (dovizborsa.com, 2 saatte bir ingest).
     */
    @GetMapping("/board")
    public BankRatesBoardResponseDto board(@RequestParam(defaultValue = "USD") String currency) {
        return boardService.load(currency);
    }
}
