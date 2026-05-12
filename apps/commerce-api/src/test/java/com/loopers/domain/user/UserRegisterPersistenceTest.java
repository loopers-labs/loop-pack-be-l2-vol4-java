package com.loopers.domain.user;

import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class UserRegisterPersistenceTest {

    private final UserService userService;
    private final UserJpaRepository userJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    UserRegisterPersistenceTest(
        UserService userService,
        UserJpaRepository userJpaRepository,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.userService = userService;
        this.userJpaRepository = userJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("회원가입 후 DB에 저장된 비밀번호는 평문이 아니다.")
    @Test
    void storedPasswordIsNotPlain() {
        // arrange
        String rawPassword = "Pass1234!";

        // act
        userService.register(
            new LoginId("loopers123"),
            new Name("김민우"),
            new Birth(LocalDate.of(1990, 1, 1)),
            new Email("user@example.com"),
            rawPassword
        );

        // assert
        User saved = userJpaRepository.findByLoginId("loopers123").orElseThrow();
        assertThat(saved.getEncodedPassword()).isNotEqualTo(rawPassword);
    }
}
