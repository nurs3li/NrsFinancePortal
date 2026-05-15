package com.nurseli.marketdata.application.bist;

public final class BistEquityUnsupportedSymbolException extends IllegalArgumentException {

    public BistEquityUnsupportedSymbolException(String symbol) {
        super("BIST günlük için desteklenmeyen sembol: " + symbol);
    }
}
