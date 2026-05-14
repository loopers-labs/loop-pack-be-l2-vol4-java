package com.loopers.interfaces.api.user;

import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class UserV1ApiE2ETest {
    private final TestRestTemplate testRestTemplate;
    private final UserJpaRepository userJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public UserV1ApiE2ETest(
        TestRestTemplate testRestTemplate,
        UserJpaRepository userJpaRepository,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.userJpaRepository = userJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    // @AfterAll
    // void tearDown() {
    //     databaseCleanUp.truncateAllTables();
    // }

    // 회원가입
    @DisplayName("POST /api/v1/users")
    @Nested
    class Signup {

    }

    // 내 정보 조회
    @DisplayName("GET /api/v1/users/myInfo")
    @Nested
    class GetUser {

    }

    // 비밀번호 변경
    @DisplayName("PATCH /api/v1/users/changePassword")
    @Nested
    class ChangePassword {
        
    }
}
