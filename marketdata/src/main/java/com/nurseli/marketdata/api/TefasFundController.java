package com.nurseli.marketdata.api;

import com.nurseli.marketdata.api.dto.TefasFundHistoryPointDto;
import com.nurseli.marketdata.api.dto.TefasFundPageDto;
import com.nurseli.marketdata.application.TefasFundService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/market/tefas")
@RequiredArgsConstructor
public class TefasFundController {

    private final TefasFundService tefasFundService;

    @GetMapping("/funds")
    public TefasFundPageDto listFunds(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "return1y") String sort,
            @RequestParam(defaultValue = "desc") String dir,
            @RequestParam(required = false) String search) {
        return tefasFundService.list(page, size, sort, dir, search);
    }

    @GetMapping("/funds/{code}/history")
    public List<TefasFundHistoryPointDto> history(
            @PathVariable String code,
            @RequestParam(defaultValue = "12") int months) {
        int safeMonths = Math.max(1, Math.min(months, 60));
        return tefasFundService.history(code, safeMonths);
    }
}
