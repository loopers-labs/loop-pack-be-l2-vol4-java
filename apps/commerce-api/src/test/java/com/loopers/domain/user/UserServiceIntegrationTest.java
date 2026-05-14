package com.loopers.domain.user;

import com.loopers.config.TestPasswordEncoderConfig;
import com.loopers.fixture.UserFixture;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;

@SpringBootTest
@Import(TestPasswordEncoderConfig.class)
public class UserServiceIntegrationTest {

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Autowired
    private UserService userService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoSpyBean
    private UserRepository userRepository;

    @DisplayName("회원 가입할 때,")
    @Nested
    class RegisterUser {

        @DisplayName("register 호출 시, userRepository.save 가 호출된다.")
        @Test
        void callsSave_whenRegister() {
            /*
             * 📝 학습 메모 — Spy vs 진짜 DB 조회 (Classicist vs Mockist)
             *
             * 위 검증 (verify) 은 "save 가 호출됐다" 만 본다.
             *   - Phase 4 의 테스트 더블 5형제 중 Spy 직접 써보기
             *   - Phase 5 의 @MockitoSpyBean 도구 익히기
             *   - 즉, 사이클 6 의 학습 의도는 "호출 흐름 검증" (Mockist 접근)
             *
             * 통합 테스트의 정석 (Classicist) 은 "진짜 DB 에 저장됐는지" 를 조회 결과로 검증하는 것:
             *
             *     UserModel saved = userRepository.findByLoginId("testuser").orElseThrow();
             *     assertThat(saved.getLoginId()).isEqualTo("testuser");
             *     assertThat(saved.getEmail()).isEqualTo("test@loopers.com");
             *
             * 차이:
             *   - Spy 검증 → "register 가 save 를 부른다" (구현 결합. 메서드명 바뀌면 깨짐)
             *   - DB 조회 검증 → "register 후 실제로 데이터가 존재한다" (결과 검증. 본질에 더 가까움)
             */
            UserModel userModel = UserFixture.createModel();

            userService.register(userModel);

            verify(userRepository).save(any(UserModel.class));
        }

        @DisplayName("같은 loginId 로 가입 시도 시, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenLoginIdAlreadyExists() {
            UserModel first  = UserFixture.createModel();
            UserModel second = UserFixture.createModel();
            userService.register(first);

            CoreException ex = assertThrows(CoreException.class, () ->
                userService.register(second)
            );

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }

        @DisplayName("register 시 비밀번호가 암호화되어 저장된다.")
        @Test
        void passwordIsEncoded_whenRegister() {
            UserModel user = UserFixture.createModel();

            UserModel saved = userService.register(user);

            assertThat(saved.getPassword()).isNotEqualTo(UserFixture.PASSWORD);
        }
    }

    @DisplayName("인증할 때,")
    @Nested
    class Authenticate {

        @DisplayName("올바른 인증 정보로 인증 시, UserModel 을 반환한다.")
        @Test
        void returnsUser_whenValidAuth() {
            userService.register(UserFixture.createModel());

            UserModel found = userService.authenticate(UserFixture.LOGIN_ID, UserFixture.PASSWORD);

            assertThat(found).isNotNull();
            assertThat(found.getLoginId()).isEqualTo(UserFixture.LOGIN_ID);
        }

        @DisplayName("비밀번호가 틀리면, UNAUTHORIZED 예외가 발생한다.")
        @Test
        void throwsUnauthorized_whenPasswordIsWrong() {
            userService.register(UserFixture.createModel());

            CoreException ex = assertThrows(CoreException.class, () ->
                userService.authenticate(UserFixture.LOGIN_ID, "WrongPass@1")
            );

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
        }

        @DisplayName("존재하지 않는 loginId 로 인증 시, UNAUTHORIZED 예외가 발생한다.")
        @Test
        void throwsUnauthorized_whenUserNotExists() {
            CoreException ex = assertThrows(CoreException.class, () ->
                userService.authenticate("nonexistent", UserFixture.PASSWORD)
            );

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
        }
    }

    @DisplayName("비밀번호를 변경할 때,")
    @Nested
    class ChangePassword {

        @DisplayName("정상 변경 시 DB 의 비밀번호가 새 값으로 암호화되어 갱신된다.")
        @Test
        void updatesEncodedPassword_whenValidChange() {
            userService.register(UserFixture.createModel());

            userService.changePassword(UserFixture.LOGIN_ID, "NewPass@99");

            UserModel updated = userService.findByLoginId(UserFixture.LOGIN_ID);
            // 새 비밀번호로 인증 성공
            assertThat(passwordEncoder.matches("NewPass@99", updated.getPassword())).isTrue();
            // 구 비밀번호로 인증 실패 — 실제로 교체됐음을 확인
            assertThat(passwordEncoder.matches(UserFixture.PASSWORD, updated.getPassword())).isFalse();
        }
    }
}
