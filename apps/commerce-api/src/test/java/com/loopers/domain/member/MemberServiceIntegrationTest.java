package com.loopers.domain.member;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class MemberServiceIntegrationTest {

    @Autowired
    private MemberService memberService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("회원가입을 할 때, ")
    @Nested
    class Register {

        @DisplayName("올바른 정보가 주어지면, 정상적으로 가입된다.")
        @Test
        void registersMember_whenAllFieldsAreValid() {
            // Arrange
            String loginId = "testUser1";

            // Act
            Member member = memberService.register(loginId, "Password1!", "홍길동", "1990-01-01", "test@example.com");

            // Assert
            assertThat(member.getLoginId()).isEqualTo(loginId);
        }

        @DisplayName("이미 가입된 로그인 ID로 가입하면, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenLoginIdAlreadyExists() {
            // Arrange
            memberService.register("testUser1", "Password1!", "홍길동", "1990-01-01", "test@example.com");

            // Act
            CoreException result = assertThrows(CoreException.class, () ->
                memberService.register("testUser1", "Password2@", "김철수", "1995-05-05", "other@example.com")
            );

            // Assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }
}
