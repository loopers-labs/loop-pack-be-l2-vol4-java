package com.loopers.application.queue;

import com.loopers.domain.user.Gender;
import com.loopers.domain.user.PasswordEncryptor;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserRepository;
import com.loopers.infrastructure.queue.QueueProperties;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;

// taskScheduler를 @MockitoBean으로 무력화해 실제 QueueAdmissionScheduler(100ms 주기)가 이 테스트가
// 대기열에 넣어둔 유저를 assertion 전에 먼저 발급/제거하지 않게 한다.
@SpringBootTest
class QueueFacadeIntegrationTest {

    private static final String LOGIN_PW = "Password1!";
    private static final String ENTRY_TOKEN_KEY_PREFIX = "queue:entry-token:";

    @MockitoBean(name = "taskScheduler")
    private TaskScheduler taskScheduler;

    @Autowired
    private QueueFacade queueFacade;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncryptor passwordEncryptor;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private QueueProperties queueProperties;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    private UserModel saveUser(String loginId) {
        return userRepository.save(new UserModel(
                loginId, LOGIN_PW, "홍길동", "1990-01-01", "user@example.com", Gender.MALE, passwordEncryptor
        ));
    }

    @DisplayName("대기열에 진입할 때,")
    @Nested
    class Enter {

        @DisplayName("첫 진입자의 position은 0이다.")
        @Test
        void returnsPositionZero_whenFirstUserEnters() {
            // given
            saveUser("user01");

            // when
            QueueInfo info = queueFacade.enter("user01", LOGIN_PW);

            // then
            assertThat(info.position()).isEqualTo(0L);
        }

        @DisplayName("두 유저가 순서대로 진입하면 순번이 진입 순서를 반영한다.")
        @Test
        void reflectsEntryOrder_whenTwoUsersEnterSequentially() {
            // given
            saveUser("user01");
            saveUser("user02");

            // when
            QueueInfo first = queueFacade.enter("user01", LOGIN_PW);
            QueueInfo second = queueFacade.enter("user02", LOGIN_PW);

            // then
            assertThat(first.position()).isLessThan(second.position());
        }
    }

    @DisplayName("대기 순번을 조회할 때,")
    @Nested
    class GetPosition {

        @DisplayName("estimatedWaitSeconds는 ceil(position / throughputPerSecond) 공식을 따른다.")
        @Test
        void calculatesEstimatedWaitSeconds_usingCeilOfPositionOverThroughput() {
            // given
            saveUser("user01");
            saveUser("user02");
            saveUser("user03");
            queueFacade.enter("user01", LOGIN_PW);
            queueFacade.enter("user02", LOGIN_PW);
            QueueInfo entered = queueFacade.enter("user03", LOGIN_PW);

            // when
            QueueInfo position = queueFacade.getPosition("user03", LOGIN_PW);

            // then
            long expected = (long) Math.ceil((double) entered.position() / queueProperties.throughputPerSecond());
            assertThat(position.estimatedWaitSeconds()).isEqualTo(expected);
        }

        @DisplayName("토큰이 없으면 token은 null이다.")
        @Test
        void returnsNullToken_whenTokenNotIssued() {
            // given
            saveUser("user01");
            queueFacade.enter("user01", LOGIN_PW);

            // when
            QueueInfo position = queueFacade.getPosition("user01", LOGIN_PW);

            // then
            assertThat(position.token()).isNull();
        }

        @DisplayName("토큰이 발급돼 있으면 token에 그 값이 채워진다.")
        @Test
        void returnsIssuedToken_whenTokenExists() {
            // given
            UserModel user = saveUser("user01");
            queueFacade.enter("user01", LOGIN_PW);
            redisTemplate.opsForValue().set(ENTRY_TOKEN_KEY_PREFIX + user.getId(), "test-token");

            // when
            QueueInfo position = queueFacade.getPosition("user01", LOGIN_PW);

            // then
            assertThat(position.token()).isEqualTo("test-token");
        }
    }
}
