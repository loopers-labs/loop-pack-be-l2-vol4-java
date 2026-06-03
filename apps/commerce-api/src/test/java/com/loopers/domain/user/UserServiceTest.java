package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UserService 순수 단위 테스트 — Repository/PasswordEncoder를 mock으로 격리해 DB·BCrypt 없이
 * signUp 중복 검사, authenticate 자격 증명 분기, changePassword 이중 검증 제거 흐름을 검증한다.
 * (실제 영속·해시 정합성은 UserServiceIntegrationTest가 Testcontainers로 검증)
 */
class UserServiceTest {

    private static final Long USER_ID = 1L;
    private static final String LOGIN_ID = "testid";
    private static final String RAW_CURRENT = "testPw1234";
    private static final String STORED_HASH = "HASH-OF-CURRENT";
    private static final LocalDate BIRTHDAY = LocalDate.of(1992, 6, 24);

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        userService = new UserService(userRepository, passwordEncoder);
    }

    /** signUp 인자로 새 UserModel을 만들 때, mock 인코더가 결정적 해시를 반환하도록 시드한다. */
    private UserModel newInputUser() {
        when(passwordEncoder.encode(anyString())).thenReturn(STORED_HASH);
        return new UserModel(LOGIN_ID, RAW_CURRENT, "테스터", BIRTHDAY, "test@example.com", passwordEncoder);
    }

    /** 저장 이후 reconstitute된 사용자 — id가 채워지고 password는 이미 해시 상태. */
    private UserModel persistedUser() {
        return UserModel.reconstitute(USER_ID, LOGIN_ID, STORED_HASH, "테스터", BIRTHDAY, "test@example.com");
    }

    @Nested
    @DisplayName("회원가입")
    class SignUp {

        @DisplayName("새 loginId면 저장하고 반환한다.")
        @Test
        void given_freshLoginId_when_signUp_then_saves() {
            UserModel input = newInputUser();
            when(userRepository.existsByLoginId(any(LoginId.class))).thenReturn(false);
            when(userRepository.save(input)).thenReturn(persistedUser());

            UserModel result = userService.signUp(input);

            assertThat(result.getId()).isEqualTo(USER_ID);
            verify(userRepository).save(input);
        }

        @DisplayName("이미 존재하는 loginId면 CONFLICT가 발생하고, 저장하지 않는다.")
        @Test
        void given_duplicateLoginId_when_signUp_then_conflict() {
            UserModel input = newInputUser();
            when(userRepository.existsByLoginId(any(LoginId.class))).thenReturn(true);

            Throwable thrown = catchThrowable(() -> userService.signUp(input));

            assertThat(((CoreException) thrown).getErrorType()).isEqualTo(ErrorType.CONFLICT);
            verify(userRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("본인 정보 조회")
    class GetMyInfo {

        @DisplayName("존재하면 회원 모델을 반환한다.")
        @Test
        void given_existing_when_getMyInfo_then_returns() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(persistedUser()));

            UserModel result = userService.getMyInfo(USER_ID);

            assertThat(result.getId()).isEqualTo(USER_ID);
        }

        @DisplayName("없으면 NOT_FOUND가 발생한다.")
        @Test
        void given_missing_when_getMyInfo_then_notFound() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

            Throwable thrown = catchThrowable(() -> userService.getMyInfo(USER_ID));

            assertThat(((CoreException) thrown).getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("인증")
    class Authenticate {

        @DisplayName("자격 증명이 맞으면 사용자를 반환한다.")
        @Test
        void given_correctCredentials_when_authenticate_then_returnsUser() {
            UserModel user = persistedUser();
            when(userRepository.findByLoginId(any(LoginId.class))).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(RAW_CURRENT, STORED_HASH)).thenReturn(true);

            UserModel result = userService.authenticate(LOGIN_ID, RAW_CURRENT);

            assertThat(result.getId()).isEqualTo(USER_ID);
        }

        @DisplayName("loginId가 없으면 UNAUTHORIZED로 통일 응대한다(계정 존재 노출 방지).")
        @Test
        void given_unknownLoginId_when_authenticate_then_unauthorized() {
            when(userRepository.findByLoginId(any(LoginId.class))).thenReturn(Optional.empty());

            Throwable thrown = catchThrowable(() -> userService.authenticate(LOGIN_ID, RAW_CURRENT));

            assertThat(((CoreException) thrown).getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
        }

        @DisplayName("비밀번호가 틀리면 UNAUTHORIZED가 발생한다.")
        @Test
        void given_wrongPassword_when_authenticate_then_unauthorized() {
            when(userRepository.findByLoginId(any(LoginId.class))).thenReturn(Optional.of(persistedUser()));
            when(passwordEncoder.matches(anyString(), eq(STORED_HASH))).thenReturn(false);

            Throwable thrown = catchThrowable(() -> userService.authenticate(LOGIN_ID, "wrong1234"));

            assertThat(((CoreException) thrown).getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
        }
    }

    @Nested
    @DisplayName("비밀번호 변경 — loginId 단일 진입(이중 검증 제거)")
    class ChangePassword {

        private static final String RAW_NEW = "newPw5678";
        private static final String NEW_HASH = "HASH-OF-NEW";

        @DisplayName("자격 증명이 맞고 새 비번이 다르면 해싱·저장한다.")
        @Test
        void given_valid_when_changePassword_then_hashesAndSaves() {
            UserModel user = persistedUser();
            when(userRepository.findByLoginId(any(LoginId.class))).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(RAW_CURRENT, STORED_HASH)).thenReturn(true);
            when(passwordEncoder.encode(RAW_NEW)).thenReturn(NEW_HASH);
            when(userRepository.save(user)).thenReturn(user);

            userService.changePassword(LOGIN_ID, RAW_CURRENT, RAW_NEW);

            assertThat(user.getPassword()).isEqualTo(NEW_HASH);
            verify(userRepository).save(user);
        }

        @DisplayName("loginId 부재 시 UNAUTHORIZED로 통일 응대한다(NOT_FOUND 노출 금지).")
        @Test
        void given_unknownLoginId_when_changePassword_then_unauthorized() {
            when(userRepository.findByLoginId(any(LoginId.class))).thenReturn(Optional.empty());

            Throwable thrown = catchThrowable(() -> userService.changePassword("nosuchid", RAW_CURRENT, RAW_NEW));

            assertThat(((CoreException) thrown).getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
            verify(userRepository, never()).save(any());
        }

        @DisplayName("현재 비번이 틀리면 UNAUTHORIZED가 발생하고, 저장하지 않는다.")
        @Test
        void given_wrongCurrent_when_changePassword_then_unauthorized() {
            when(userRepository.findByLoginId(any(LoginId.class))).thenReturn(Optional.of(persistedUser()));
            when(passwordEncoder.matches("wrong", STORED_HASH)).thenReturn(false);

            Throwable thrown = catchThrowable(() -> userService.changePassword(LOGIN_ID, "wrong", RAW_NEW));

            assertThat(((CoreException) thrown).getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
            verify(userRepository, never()).save(any());
        }

        @DisplayName("새 비번이 현재 비번과 같으면 BAD_REQUEST가 발생하고, 저장하지 않는다.")
        @Test
        void given_sameAsCurrent_when_changePassword_then_badRequest() {
            when(userRepository.findByLoginId(any(LoginId.class))).thenReturn(Optional.of(persistedUser()));
            when(passwordEncoder.matches(RAW_CURRENT, STORED_HASH)).thenReturn(true);

            Throwable thrown = catchThrowable(() -> userService.changePassword(LOGIN_ID, RAW_CURRENT, RAW_CURRENT));

            assertThat(((CoreException) thrown).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            verify(userRepository, never()).save(any());
        }
    }
}
