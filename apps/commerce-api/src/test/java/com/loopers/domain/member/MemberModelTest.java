package com.loopers.domain.member;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MemberModelTest {

    @DisplayName("회원 모델을 생성할 때, ")
    @Nested

    class Create {

        @DisplayName("올바른 정보를 입력하면 성공한다.")
        @Test
        void creates_successfully_with_valid_inputs() {
            MemberModel member = new MemberModel("userId", "Password1!", "user@example.com", "홍길동", "19900101");

            assertAll(
                () -> assertThat(member.getUserId()).isEqualTo("userId"),
                () -> assertThat(member.getEmail()).isEqualTo("user@example.com"),
                () -> assertThat(member.getUsername()).isEqualTo("홍길동"),
                () -> assertThat(member.getBirthDate()).isEqualTo("19900101")
            );
        }

        @DisplayName("로그인 ID가 비어있으면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throws_when_userId_is_blank() {
            CoreException result = assertThrows(CoreException.class,
                () -> new MemberModel("  ", "Password1!", "user@example.com", "홍길동", "19900101"));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("로그인 ID에 영문, 숫자 외의 문자가 있으면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throws_when_userId_has_invalid_chars() {
            CoreException result = assertThrows(CoreException.class,
                () -> new MemberModel("user@id!", "Password1!", "user@example.com", "홍길동", "19900101"));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이름이 비어있으면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throws_when_username_is_blank() {
            CoreException result = assertThrows(CoreException.class,
                () -> new MemberModel("userId", "Password1!", "user@example.com", "", "19900101"));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이메일 형식이 올바르지 않으면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throws_when_email_is_invalid() {
            CoreException result = assertThrows(CoreException.class,
                () -> new MemberModel("userId", "Password1!", "not-an-email", "홍길동", "19900101"));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("생년월일이 YYYYMMDD 형식이 아니면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throws_when_birthDate_format_is_invalid() {
            CoreException result = assertThrows(CoreException.class,
                () -> new MemberModel("userId", "Password1!", "user@example.com", "홍길동", "1990-01-01"));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("비밀번호가 8자 미만이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throws_when_password_is_too_short() {
            CoreException result = assertThrows(CoreException.class,
                () -> new MemberModel("userId", "Pass1!", "user@example.com", "홍길동", "19900101"));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("비밀번호가 16자를 초과하면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throws_when_password_is_too_long() {
            CoreException result = assertThrows(CoreException.class,
                () -> new MemberModel("userId", "Password1!Password1!", "user@example.com", "홍길동", "19900101"));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("비밀번호에 허용되지 않는 문자가 있으면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throws_when_password_has_invalid_chars() {
            CoreException result = assertThrows(CoreException.class,
                () -> new MemberModel("userId", "패스워드1234!!", "user@example.com", "홍길동", "19900101"));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("비밀번호에 생년월일이 포함되면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throws_when_password_contains_birthDate() {
            CoreException result = assertThrows(CoreException.class,
                () -> new MemberModel("userId", "19900101Ab!", "user@example.com", "홍길동", "19900101"));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("회원 모델을 조회할 때, ")
    @Nested
    class Read {

        @DisplayName("이름의 마지막 글자가 마스킹되어 반환된다.")
        @Test
        void returns_username_with_last_char_masked() {
            MemberModel member = new MemberModel("userId", "Password1!", "user@example.com", "홍길동", "19900101");

            assertThat(member.getMaskedUsername()).isEqualTo("홍길*");
        }
    }

    @DisplayName("회원 모델을 수정할 때, ")
    @Nested
    class Update {

        @DisplayName("올바른 정보로 비밀번호를 수정하면 성공한다.")
        @Test
        void updates_password_successfully() {
            MemberModel member = new MemberModel("userId", "Password1!", "user@example.com", "홍길동", "19900101");

            member.updatePassword("Password1!", "NewPass2@");

            assertThat(member.getPassword()).isNotEqualTo("Password1!");
        }

        @DisplayName("기존 비밀번호가 일치하지 않으면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throws_when_current_password_is_wrong() {
            MemberModel member = new MemberModel("userId", "Password1!", "user@example.com", "홍길동", "19900101");

            CoreException result = assertThrows(CoreException.class,
                () -> member.updatePassword("WrongPass1!", "NewPass2@"));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("새 비밀번호가 현재 비밀번호와 동일하면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throws_when_new_password_is_same_as_current() {
            MemberModel member = new MemberModel("userId", "Password1!", "user@example.com", "홍길동", "19900101");

            CoreException result = assertThrows(CoreException.class,
                () -> member.updatePassword("Password1!", "Password1!"));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("새 비밀번호에 허용되지 않는 문자가 있으면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throws_when_new_password_has_invalid_chars() {
            MemberModel member = new MemberModel("userId", "Password1!", "user@example.com", "홍길동", "19900101");

            CoreException result = assertThrows(CoreException.class,
                () -> member.updatePassword("Password1!", "패스워드1234!!"));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("새 비밀번호에 생년월일이 포함되면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throws_when_new_password_contains_birthDate() {
            MemberModel member = new MemberModel("userId", "Password1!", "user@example.com", "홍길동", "19900101");

            CoreException result = assertThrows(CoreException.class,
                () -> member.updatePassword("Password1!", "19900101Ab!"));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
