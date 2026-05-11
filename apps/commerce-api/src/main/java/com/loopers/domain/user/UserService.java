package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@RequiredArgsConstructor
@Component
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserModel signUp(String loginId, String rawPassword, String name, LocalDate birthDate, String email) {
        if (userRepository.existsByLoginId(loginId)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 존재하는 로그인 ID 입니다.");
        }
        UserModel.validateRawPassword(rawPassword, birthDate);
        String encoded = passwordEncoder.encode(rawPassword);
        return userRepository.save(new UserModel(loginId, encoded, name, birthDate, email));
    }

    @Transactional(readOnly = true)
    public UserModel authenticate(String loginId, String rawPassword) {
        UserModel user = userRepository.findByLoginId(loginId)
            .orElseThrow(() -> new CoreException(ErrorType.UNAUTHORIZED, "인증 정보가 올바르지 않습니다."));
        if (!passwordEncoder.matches(rawPassword, user.getEncodedPassword())) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "인증 정보가 올바르지 않습니다.");
        }
        return user;
    }

    @Transactional(readOnly = true)
    public UserModel getById(Long id) {
        return userRepository.findById(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "유저를 찾을 수 없습니다."));
    }

    @Transactional
    public void changePassword(Long userId, String currentRawPassword, String newRawPassword) {
        UserModel user = userRepository.findById(userId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "유저를 찾을 수 없습니다."));
        if (!passwordEncoder.matches(currentRawPassword, user.getEncodedPassword())) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "현재 비밀번호가 일치하지 않습니다.");
        }
        UserModel.validateRawPassword(newRawPassword, user.getBirthDate());
        if (passwordEncoder.matches(newRawPassword, user.getEncodedPassword())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "새 비밀번호는 현재 비밀번호와 같을 수 없습니다.");
        }
        user.changePassword(passwordEncoder.encode(newRawPassword));
    }
}
