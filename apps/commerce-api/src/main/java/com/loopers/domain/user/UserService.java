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
    public UserModel signUp(String loginId, String rawPasswordValue, String name, String birthDate, String email) {
        if (userRepository.existsByLoginId(loginId)) {
            throw new CoreException(ErrorType.CONFLICT, "[loginId = " + loginId + "] 이미 사용 중인 ID 입니다.");
        }
        RawPassword raw = new RawPassword(rawPasswordValue);
        if (birthDate != null && raw.value().contains(birthDate.replace("-", ""))) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호에 생년월일을 포함할 수 없습니다.");
        }
        EncodedPassword encoded = passwordEncoder.encode(raw);
        UserModel user = new UserModel(loginId, encoded, name, birthDate, email);
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public UserModel authenticate(String loginId, String rawPasswordValue) {
        UserModel user = userRepository.findByLoginId(loginId)
            .orElseThrow(() -> new CoreException(ErrorType.UNAUTHORIZED, "인증에 실패했습니다."));
        if (!passwordEncoder.matches(rawPasswordValue, user.getPassword())) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "인증에 실패했습니다.");
        }
        return user;
    }

    @Transactional
    public void changePassword(String loginId, String oldRawPasswordValue, String newRawPasswordValue) {
        UserModel user = userRepository.findByLoginId(loginId)
            .orElseThrow(() -> new CoreException(ErrorType.UNAUTHORIZED, "기존 비밀번호가 일치하지 않습니다."));
        if (!passwordEncoder.matches(oldRawPasswordValue, user.getPassword())) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "기존 비밀번호가 일치하지 않습니다.");
        }
        RawPassword newRawPassword = new RawPassword(newRawPasswordValue);
        user.updatePassword(newRawPassword, passwordEncoder);
        userRepository.save(user);
    }
}
