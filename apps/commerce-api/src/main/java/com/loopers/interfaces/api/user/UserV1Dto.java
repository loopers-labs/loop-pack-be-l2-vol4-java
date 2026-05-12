package com.loopers.interfaces.api.user;

public class UserV1Dto {

    public record SignUpRequest(
        String loginId,
        String password,
        String name,
        String birthDate,
        String email
    ) {
    }
}
