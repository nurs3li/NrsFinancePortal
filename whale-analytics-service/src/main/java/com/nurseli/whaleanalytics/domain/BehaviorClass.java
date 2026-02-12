package com.nurseli.whaleanalytics.domain;

public enum BehaviorClass {

    CONSERVATIVE,   // düşük hacim, stabil
    AGGRESSIVE,     // yüksek hacim, hızlı trend
    STRATEGIC,      // accumulation/distribution pattern
    SPECULATIVE,    // volatile + orta hacim
    MANIPULATIVE    // pump & dump veya high frequency
}
