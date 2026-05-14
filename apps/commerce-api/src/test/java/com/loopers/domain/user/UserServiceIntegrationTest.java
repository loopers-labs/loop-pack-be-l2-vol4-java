package com.loopers.domain.user;

import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class UserServiceIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private UserModel user;

    @BeforeEach
    void setUp() {
        user = new UserModel("user123", "encodedPassword!", "홍길동", LocalDate.of(1990, 1, 15), "test@example.com");
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("register()를 호출할 때,")
    @Nested
    class Register {

        @DisplayName("Given 유효한 유저 정보 / When 등록 요청 / Then DB에 저장되고 ID가 부여된 UserModel이 반환된다.")
        @Test
        void savesUser_whenValidUserIsProvided() {
            // act
            UserModel result = userService.register(user);

            // assert
            assertAll(
                () -> assertThat(result.getId()).isNotNull(),
                () -> assertThat(result.getLoginId()).isEqualTo(user.getLoginId()),
                () -> assertThat(result.getName()).isEqualTo(user.getName()),
                () -> assertThat(result.getEmail()).isEqualTo(user.getEmail()),
                () -> assertThat(userJpaRepository.findById(result.getId())).isPresent()
            );
        }

        @DisplayName("Given 이미 존재하는 loginId / When 등록 요청 / Then CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenLoginIdIsDuplicated() {
            // arrange
            userJpaRepository.save(user);
            UserModel duplicate = new UserModel(
                "user123", "anotherEncoded!", "김철수", LocalDate.of(1995, 5, 20), "other@example.com"
            );

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                userService.register(duplicate)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }
}
