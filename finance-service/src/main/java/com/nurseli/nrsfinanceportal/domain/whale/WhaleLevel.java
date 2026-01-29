package com.nurseli.nrsfinanceportal.domain.whale;

public enum WhaleLevel {
    NONE,
    L1_LARGE_TRADER,
    L2_WHALE,
    L3_MEGA_WHALE;

    public boolean isWhale() {
        return this == L2_WHALE || this == L3_MEGA_WHALE;
    }
}
