package com.loopers.user.interfaces.api;

import com.loopers.user.application.UserResult;

import java.time.LocalDate;

public class UserV1Response {

    public record Detail(
        Long id,
        String loginId,
        String name,
        LocalDate birthDate,
        String email
    ) {
        public static Detail from(UserResult.Detail result) {
            return new Detail(
                result.id(),
                result.loginId(),
                result.name(),
                result.birthDate(),
                result.email()
            );
        }
    }

    public record Masked(
        String loginId,
        String name,
        LocalDate birthDate,
        String email
    ) {
        public static Masked from(UserResult.Detail result) {
            return new Masked(
                result.loginId(),
                maskLastCharacter(result.name()),
                result.birthDate(),
                result.email()
            );
        }

        /** 이름의 마지막 글자를 * 로 마스킹한다. (예: "김루퍼" -> "김루*", "김" -> "*") */
        private static String maskLastCharacter(String name) {
            return name.substring(0, name.length() - 1) + "*";
        }
    }
}
