package com.nurseli.nrsfinanceportal.common.dto;

public record InternalUserInfoResponse(
        String sub,
        String email,
        boolean emailVerified
) {}