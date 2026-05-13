package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserModel signUp(String loginId, String rawPassword, String name, String birthDate, String email) {
        if (userRepository.existsByLoginId(loginId)) {
            throw new CoreException(ErrorType.CONFLICT, "[loginId = " + loginId + "] 이미 사용 중인 ID 입니다.");
        }
        Password raw = new Password(rawPassword);
        if (birthDate != null && raw.value().contains(birthDate.replace("-", ""))) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호에 생년월일을 포함할 수 없습니다.");
        }
        Password encodedPassword = new Password(passwordEncoder.encode(raw.value()));
        UserModel user = new UserModel(loginId, encodedPassword, name, birthDate, email);
        return userRepository.save(user);
    }
}
