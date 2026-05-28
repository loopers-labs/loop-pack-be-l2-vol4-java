package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;

class PasswordVOTest {

    private static final LocalDate BIRTH_DATE =
            LocalDate.of(1999, 1, 15);

    @Nested
    @DisplayName("fromEncoded")
    class FromEncoded {

        @Test
        @DisplayName("encoded password가 null이면 예외가 발생한다")
        void fail_when_encoded_password_is_null() {

            assertThatThrownBy(() ->
                    PasswordVO.fromEncoded(null)
            )
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("비밀번호는 비어있을 수 없습니다");
        }

        @Test
        @DisplayName("encoded password가 blank이면 예외가 발생한다")
        void fail_when_encoded_password_is_blank() {

            assertThatThrownBy(() ->
                    PasswordVO.fromEncoded(" ")
            )
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("비밀번호는 비어있을 수 없습니다");
        }

        @Test
        @DisplayName("encoded password 생성에 성공한다")
        void success() {

            PasswordVO password =
                    PasswordVO.fromEncoded("encoded-password");

            assertThat(password.value())
                    .isEqualTo("encoded-password");
        }
    }

    @Nested
    @DisplayName("validatePolicy")
    class ValidatePolicy {

        @Nested
        @DisplayName("null/blank 검증")
        class NullBlankValidation {

            @Test
            @DisplayName("비밀번호가 null이면 예외가 발생한다")
            void fail_when_password_is_null() {

                assertThatThrownBy(() ->
                        PasswordVO.validatePolicy(
                                null,
                                BIRTH_DATE
                        )
                )
                        .isInstanceOf(CoreException.class)
                        .hasMessageContaining("비밀번호는 비어있을 수 없습니다");
            }

            @Test
            @DisplayName("비밀번호가 blank이면 예외가 발생한다")
            void fail_when_password_is_blank() {

                assertThatThrownBy(() ->
                        PasswordVO.validatePolicy(
                                " ",
                                BIRTH_DATE
                        )
                )
                        .isInstanceOf(CoreException.class)
                        .hasMessageContaining("비밀번호는 비어있을 수 없습니다");
            }
        }

        @Nested
        @DisplayName("길이 검증")
        class LengthValidation {

            @Test
            @DisplayName("7자이면 예외가 발생한다")
            void fail_when_length_is_7() {

                assertThatThrownBy(() ->
                        PasswordVO.validatePolicy(
                                "Ab1!123",
                                BIRTH_DATE
                        )
                )
                        .isInstanceOf(CoreException.class)
                        .hasMessageContaining("8자 이상");
            }

            @Test
            @DisplayName("8자이면 성공한다")
            void success_when_length_is_8() {

                assertThatCode(() ->
                        PasswordVO.validatePolicy(
                                "Ab1!1234",
                                BIRTH_DATE
                        )
                )
                        .doesNotThrowAnyException();
            }

            @Test
            @DisplayName("16자이면 성공한다")
            void success_when_length_is_16() {

                assertThatCode(() ->
                        PasswordVO.validatePolicy(
                                "Ab1!123456789012",
                                BIRTH_DATE
                        )
                )
                        .doesNotThrowAnyException();
            }

            @Test
            @DisplayName("17자이면 예외가 발생한다")
            void fail_when_length_is_17() {

                assertThatThrownBy(() ->
                        PasswordVO.validatePolicy(
                                "Ab1!1234567890123",
                                BIRTH_DATE
                        )
                )
                        .isInstanceOf(CoreException.class)
                        .hasMessageContaining("16자 이하");
            }
        }

        @Nested
        @DisplayName("허용 문자 검증")
        class CharacterValidation {

            @Test
            @DisplayName("영문 대소문자, 숫자, 특수문자 조합이면 성공한다")
            void success_when_valid_characters() {

                assertThatCode(() ->
                        PasswordVO.validatePolicy(
                                "Abcd1234!@",
                                BIRTH_DATE
                        )
                )
                        .doesNotThrowAnyException();
            }

            @Test
            @DisplayName("한글이 포함되면 예외가 발생한다")
            void fail_when_contains_korean() {

                assertThatThrownBy(() ->
                        PasswordVO.validatePolicy(
                                "Abcd한글123!",
                                BIRTH_DATE
                        )
                )
                        .isInstanceOf(CoreException.class)
                        .hasMessageContaining("영문 대소문자");
            }

            @Test
            @DisplayName("공백이 포함되면 예외가 발생한다")
            void fail_when_contains_whitespace() {

                assertThatThrownBy(() ->
                        PasswordVO.validatePolicy(
                                "Abcd 123!",
                                BIRTH_DATE
                        )
                )
                        .isInstanceOf(CoreException.class)
                        .hasMessageContaining("영문 대소문자");
            }

            @Test
            @DisplayName("이모지가 포함되면 예외가 발생한다")
            void fail_when_contains_emoji() {

                assertThatThrownBy(() ->
                        PasswordVO.validatePolicy(
                                "Abcd1234😀",
                                BIRTH_DATE
                        )
                )
                        .isInstanceOf(CoreException.class)
                        .hasMessageContaining("영문 대소문자");
            }
        }

        @Nested
        @DisplayName("생년월일 포함 검증")
        class BirthDateValidation {

            @Test
            @DisplayName("yyyyMMdd 형식의 생년월일이 포함되면 예외가 발생한다")
            void fail_when_contains_full_birth_date() {

                assertThatThrownBy(() ->
                        PasswordVO.validatePolicy(
                                "Abcd19990115!",
                                BIRTH_DATE
                        )
                )
                        .isInstanceOf(CoreException.class)
                        .hasMessageContaining("생년월일");
            }

            @Test
            @DisplayName("yyMMdd 형식의 생년월일이 포함되면 예외가 발생한다")
            void fail_when_contains_yearless_birth_date() {

                assertThatThrownBy(() ->
                        PasswordVO.validatePolicy(
                                "Abcd990115!",
                                BIRTH_DATE
                        )
                )
                        .isInstanceOf(CoreException.class)
                        .hasMessageContaining("생년월일");
            }

            @Test
            @DisplayName("MMdd 형식의 생년월일이 포함되면 예외가 발생한다")
            void fail_when_contains_month_day_birth_date() {

                assertThatThrownBy(() ->
                        PasswordVO.validatePolicy(
                                "Abcd0115!",
                                BIRTH_DATE
                        )
                )
                        .isInstanceOf(CoreException.class)
                        .hasMessageContaining("생년월일");
            }

            @Test
            @DisplayName("생년월일이 포함되지 않으면 성공한다")
            void success_when_not_contains_birth_date() {

                assertThatCode(() ->
                        PasswordVO.validatePolicy(
                                "SafePasswordVO1!",
                                BIRTH_DATE
                        )
                )
                        .doesNotThrowAnyException();
            }
        }

        @Test
        @DisplayName("모든 정책을 만족하면 성공한다")
        void success_when_valid_password() {

            assertThatCode(() ->
                    PasswordVO.validatePolicy(
                            "SafePass12!",
                            BIRTH_DATE
                    )
            )
                    .doesNotThrowAnyException();
        }
    }
}
