package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class EmailTest {

    @Nested
    @DisplayName("Email 검증")
    class EmailValidation {

        @DisplayName("이메일이 null이거나 공백이면 BAD_REQUEST 예외가 발생한다")
        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "   ", "\t", "\n"})
        void given_nullOrBlankEmail_when_createEmail_then_throwsBadRequestException(String invalidEmail) {
            CoreException result = assertThrows(
                    CoreException.class,
                    () -> new Email(invalidEmail)
            );
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이메일이 형식에 맞지 않으면 BAD_REQUEST 예외가 발생한다")
        @ParameterizedTest
        @ValueSource(strings = {
                "plainaddress",              // @ 없음
                "@no-local.com",             // 로컬 파트 없음
                "user@",                     // 도메인 없음
                "user@domain",               // TLD 없음
                "user@.com",                 // 도메인 시작이 점
                "user @domain.com",          // 공백 포함
                "user@@domain.com",          // @ 중복
                "한글@domain.com",            // 국제화 이메일 (한글 로컬)
                "user@한글.com",              // 국제화 이메일 (한글 도메인)
                "user@domain.한국"            // 국제화 이메일 (한글 TLD)
        })
        void given_invalidEmailFormat_when_createEmail_then_throwsBadRequestException(String invalidEmail) {
            CoreException result = assertThrows(
                    CoreException.class,
                    () -> new Email(invalidEmail)
            );
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이메일이 100자를 초과하면 BAD_REQUEST 예외가 발생한다")
        @Test
        void given_emailOverMaxLength_when_createEmail_then_throwsBadRequestException() {
            // 101자: 92 + "@test.com"(9) = 101자
            String tooLongEmail = "a".repeat(92) + "@test.com";

            CoreException result = assertThrows(
                    CoreException.class,
                    () -> new Email(tooLongEmail)
            );
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        // 해피케이스
        @DisplayName("이메일이 유효한 형식이고 100자 이하이면 정상 생성된다")
        @ParameterizedTest
        @ValueSource(strings = {
                "user@domain.com",                   // 기본 형식
                "user.name@domain.com",              // 로컬 파트에 점
                "user+tag@domain.com",               // 플러스 태그
                "user_name@domain.com",              // 언더스코어
                "user-name@domain.com",              // 하이픈
                "user123@domain.com",                // 숫자 포함
                "u@d.co",                            // 짧은 형식
                "user@sub.domain.com",               // 서브 도메인
                "user@domain.co.kr"                  // 다단계 TLD
        })
        void given_validEmail_when_createEmail_then_createsEmail(String validEmail) {
            // Act
            Email email = new Email(validEmail);

            // Assert
            assertThat(email).isNotNull();
            assertThat(email.getValue()).isEqualTo(validEmail);
        }
    }
}
