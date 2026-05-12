package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserModel createUser(
            String loginId,
            String password,
            String name,
            LocalDate birthDate,
            String email
    ) {
        validatePasswordNotContainsBirthDate(password, birthDate);

        if (userRepository.existsByLoginId(loginId)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 사용 중인 로그인 ID입니다.");
        }

        String encodedPassword = passwordEncoder.encode(password);
        UserModel userModel = UserModel.of(loginId, encodedPassword, name, birthDate, email);

        return userRepository.save(userModel);
    }

    private void validatePasswordNotContainsBirthDate(String password, LocalDate birthDate) {
        String birth = birthDate.format(DateTimeFormatter.BASIC_ISO_DATE);
        if (password.contains(birth)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호에 생년월일을 포함할 수 없습니다.");
        }
    }
}
