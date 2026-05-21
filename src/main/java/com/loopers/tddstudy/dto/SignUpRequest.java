package com.loopers.tddstudy.dto;

    public record SignUpRequest(
            String loginId,
            String loginPw,
            String name,
            String birthDate,
            String email
    ) {}


