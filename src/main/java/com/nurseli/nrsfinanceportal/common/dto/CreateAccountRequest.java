package com.nurseli.nrsfinanceportal.common.dto;

import jakarta.validation.constraints.NotNull;

public class CreateAccountRequest {

    @NotNull
    private Long userId;

    @NotNull
    private String accountType;

    public Long getUserId() {
        return userId;
    }

    public String getAccountType() {
        return accountType;
    }
}
