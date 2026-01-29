package com.nurseli.whaleanalytics.domain;
public enum WhaleLevel {
    NONE,
    L1_LARGE_TRADER,
    L2_WHALE,
    L3_MEGA_WHALE;

    public boolean isAlertLevel() {
        return this == L2_WHALE || this == L3_MEGA_WHALE;
    }
}
