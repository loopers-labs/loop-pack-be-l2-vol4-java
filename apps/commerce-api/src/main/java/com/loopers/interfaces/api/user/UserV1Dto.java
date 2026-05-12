package com.loopers.interfaces.api.user;

import com.loopers.application.user.SignUpCommand;
import com.loopers.application.user.UserInfo;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

public class UserV1Dto {

    @Schema(name = "SignUpRequest", description = "회원 가입 요청")
    public record SignUpRequest(
        @Schema(description = "로그인 ID (영문/숫자만 사용)", example = "loopers01", requiredMode = Schema.RequiredMode.REQUIRED)
        String loginId,
        @Schema(description = "비밀번호 (8~16자, 영문 대소문자/숫자/특수문자, 생년월일 미포함)", example = "Loopers!2026", requiredMode = Schema.RequiredMode.REQUIRED)
        String password,
        @Schema(description = "이름", example = "김성호", requiredMode = Schema.RequiredMode.REQUIRED)
        String name,
        @Schema(description = "생년월일", example = "1993-11-03", requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDate birthDate,
        @Schema(description = "이메일 (xx@yy.zz)", example = "loopers@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
        String email
    ) {
        public SignUpCommand toCommand() {
            return new SignUpCommand(loginId, password, name, birthDate, email);
        }
    }

    @Schema(name = "ChangePasswordRequest", description = "비밀번호 변경 요청")
    public record ChangePasswordRequest(
        @Schema(description = "현재 비밀번호 (저장된 값과 일치해야 함)", example = "Loopers!2026", requiredMode = Schema.RequiredMode.REQUIRED)
        String currentPassword,
        @Schema(description = "새 비밀번호 (8~16자, 영문/숫자/특수문자, 생년월일 미포함, 현재 비밀번호와 달라야 함)", example = "NewLoopers!9999", requiredMode = Schema.RequiredMode.REQUIRED)
        String newPassword
    ) {}

    @Schema(name = "UserResponse", description = "회원 정보 응답")
    public record UserResponse(
        @Schema(description = "회원 PK", example = "1")
        Long id,
        @Schema(description = "로그인 ID", example = "loopers01")
        String loginId,
        @Schema(description = "이름", example = "김성호")
        String name,
        @Schema(description = "생년월일", example = "1993-11-03")
        LocalDate birthDate,
        @Schema(description = "이메일", example = "loopers@example.com")
        String email
    ) {
        public static UserResponse from(UserInfo info) {
            return new UserResponse(
                info.id(),
                info.loginId(),
                info.name(),
                info.birthDate(),
                info.email()
            );
        }
    }

    @Schema(name = "MyInfoResponse", description = "내 정보 응답")
    public record MyInfoResponse(
        @Schema(description = "로그인 ID", example = "loopers01")
        String loginId,
        @Schema(description = "이름 (마지막 글자는 * 로 마스킹)", example = "김성*")
        String name,
        @Schema(description = "생년월일", example = "1993-11-03")
        LocalDate birthDate,
        @Schema(description = "이메일", example = "loopers@example.com")
        String email
    ) {
        public static MyInfoResponse from(UserInfo info) {
            return new MyInfoResponse(
                info.loginId(),
                maskLastChar(info.name()),
                info.birthDate(),
                info.email()
            );
        }
    }

    private static String maskLastChar(String value) {
        if (value.length() <= 1) {
            return "*";
        }
        return value.substring(0, value.length() - 1) + "*";
    }
}
