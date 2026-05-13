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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

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
             *     // (예시 — findByLoginId 가 사이클 12 에서 추가되면 가능)
             *     UserModel saved = userRepository.findByLoginId("testuser").orElseThrow();
             *     assertThat(saved.getLoginId()).isEqualTo("testuser");
             *     assertThat(saved.getEmail()).isEqualTo("test@loopers.com");
             *
             * 차이:
             *   - Spy 검증 → "register 가 save 를 부른다" (구현 결합. 메서드명 바뀌면 깨짐)
             *   - DB 조회 검증 → "register 후 실제로 데이터가 존재한다" (결과 검증. 본질에 더 가까움)
             *
             * 사이클 7~8 부터는 Classicist 접근 (진짜 DB 조회) 으로 전환한다.
             *   - 사이클 7: 중복 가입 시 CONFLICT — 진짜 DB unique 제약 검증
             *   - 사이클 8: 비밀번호 암호화 저장 — DB 의 password 가 평문과 다름을 조회로 확인
             */

            // arrange
            UserModel userModel = UserFixture.createModel();

            // act
            userService.register(userModel);

            // assert — Spy 로 "save 가 호출됐다" 만 검증
            verify(userRepository).save(any(UserModel.class));
        }


        @DisplayName("같은 loginId 로 가입 시도 시, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenLoginIdAlreadyExists() {
            // arrange — 동일 loginId 로 두 번 시도
            UserModel first  = UserFixture.createModel();
            UserModel second = UserFixture.createModel();
            userService.register(first);

            // act
            CoreException ex = assertThrows(CoreException.class, () ->
                    userService.register(second)
            );

            //assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }

        @DisplayName("register 시 비밀번호가 암호화되어 저장된다.")
        @Test
        void passwordIsEncoded_whenRegister() {
            // arrange
            UserModel user = UserFixture.createModel();

            // act
            UserModel saved = userService.register(user);

            // assert — 저장된 password 가 평문과 다름
            // 약한 검증 : 알고리즘 유무 관계 없이 평문이 아닌지만 확인
            assertThat(saved.getPassword()).isNotEqualTo(UserFixture.PASSWORD);

            // 강한 검증 : 실제 사용된 객체 변환 값의 알고리즘 확인
            //assertThat(saved.getPassword()).isEqualTo("encoded:" + rawPassword);
        }
    }






}
