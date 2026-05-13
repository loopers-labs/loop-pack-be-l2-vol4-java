package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@RequiredArgsConstructor
@Component
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public User register(
        String loginId,
        String password,
        String name,
        LocalDate birth,
        String email
    ) {
        LoginId loginIdValue = new LoginId(loginId);
        Email emailValue = new Email(email);
        Birth birthValue = new Birth(birth);

        if (userRepository.existsByLoginId(loginIdValue)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 가입된 로그인 ID 입니다.");
        }
        if (userRepository.existsByEmail(emailValue)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 등록된 이메일입니다.");
        }

        Password passwordValue = Password.of(password, birthValue);
        String encodedPassword = passwordEncoder.encode(passwordValue.value());

        User user = new User(loginIdValue, new Name(name), birthValue, emailValue, encodedPassword);
        return userRepository.save(user);
    }
}
