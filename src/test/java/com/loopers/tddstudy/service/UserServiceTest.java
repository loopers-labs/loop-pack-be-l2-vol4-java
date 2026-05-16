package com.loopers.tddstudy.service;

import com.loopers.tddstudy.domain.User;
import com.loopers.tddstudy.dto.LoginRequest;
import com.loopers.tddstudy.dto.SignUpRequest;
import com.loopers.tddstudy.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @Test
    @DisplayName("회원가입에 성공한다")
    void signUp_success() {
        SignUpRequest request = new SignUpRequest("lilpa123", "Pass1234!", "김릴파", "19901225", "lilpa@email.com");

        when(userRepository.existsByLoginId("lilpa123")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        User result = userService.signUp(request);

        assertThat(result.getLoginId()).isEqualTo("lilpa123");
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    @DisplayName("중복된 loginId로 가입하면 예외가 발생한다")
    void signUp_duplicateLoginId_throwsException() {
        SignUpRequest request = new SignUpRequest("lilpa123", "Pass1234!", "김릴파", "19901225", "lilpa@email.com");

        when(userRepository.existsByLoginId("lilpa123")).thenReturn(true);

        assertThatThrownBy(() -> userService.signUp(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이미 사용중인 로그인 ID입니다.");
    }

    @Test
    @DisplayName("로그인에 성공한다")
    void login_success() {
        LoginRequest request = new LoginRequest("lilpa123", "Pass1234!");
        User user = new User("lilpa123", "Pass1234!", "김릴파", "19901225", "lilpa@email.com");

        when(userRepository.findByLoginId("lilpa123")).thenReturn(Optional.of(user));

        User result = userService.login(request);

        assertThat(result.getLoginId()).isEqualTo("lilpa123");
    }

    @Test
    @DisplayName("존재하지 않는 loginId로 로그인하면 예외가 발생한다")
    void login_notFound_throwsException() {
        LoginRequest request = new LoginRequest("lilpa123", "Pass1234!");

        when(userRepository.findByLoginId("lilpa123")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.login(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("존재하지 않는 사용자입니다.");
    }

    @Test
    @DisplayName("비밀번호가 틀리면 예외가 발생한다")
    void login_wrongPassword_throwsException() {
        LoginRequest request = new LoginRequest("lilpa123", "Wrong1234!");
        User user = new User("lilpa123", "Pass1234!", "김릴파", "19901225", "lilpa@email.com");

        when(userRepository.findByLoginId("lilpa123")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.login(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("비밀번호가 일치하지 않습니다.");
    }
}