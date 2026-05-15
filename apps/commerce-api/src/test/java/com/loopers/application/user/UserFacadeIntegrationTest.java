package com.loopers.application.user;

import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
class UserFacadeIntegrationTest {

    private final UserFacade userFacade;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    UserFacadeIntegrationTest(UserFacade userFacade, DatabaseCleanUp databaseCleanUp) {
        this.userFacade = userFacade;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("내 정보 조회 시, ")
    @Nested
    class GetMyInfo {

        @DisplayName("존재하는 회원 ID 면, 해당 회원의 UserInfo 를 반환한다.")
        @Test
        void returnsUserInfo_whenIdExists() {
            // arrange
            UserInfo saved = userFacade.signUp(new SignUpCommand(
                "loopers01", "Loopers!2026", "김성호", LocalDate.of(1993, 11, 3), "loopers@example.com"
            ));

            // act
            UserInfo result = userFacade.getMyInfo(saved.id());

            // assert
            assertAll(
                () -> assertThat(result.id()).isEqualTo(saved.id()),
                () -> assertThat(result.loginId()).isEqualTo("loopers01"),
                () -> assertThat(result.name()).isEqualTo("김성호"),
                () -> assertThat(result.birthDate()).isEqualTo(LocalDate.of(1993, 11, 3)),
                () -> assertThat(result.email()).isEqualTo("loopers@example.com")
            );
        }
    }
}
