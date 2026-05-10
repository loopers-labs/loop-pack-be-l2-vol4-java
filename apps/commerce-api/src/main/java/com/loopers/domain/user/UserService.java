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

    @Transactional
    public UserModel register(String loginId, String rawPassword, String name, LocalDate birthDate, String email) {
        userRepository.findByLoginId(loginId).ifPresent(u -> {
            throw new CoreException(ErrorType.CONFLICT, "이미 가입된 로그인 ID입니다.");
        });
        UserModel user = new UserModel(loginId, rawPassword, name, birthDate, email);
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public UserModel getUser(String loginId, String rawPassword) {
        UserModel user = userRepository.findByLoginId(loginId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 회원입니다."));
        if (!user.matchesPassword(rawPassword)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호가 일치하지 않습니다.");
        }
        return user;
    }

    @Transactional
    public void changePassword(String loginId, String rawCurrentPassword, String rawNewPassword) {
        UserModel user = userRepository.findByLoginId(loginId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 회원입니다."));
        user.changePassword(rawCurrentPassword, rawNewPassword);
        userRepository.save(user);
    }
}
