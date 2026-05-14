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
    private final PasswordEncryptor passwordEncryptor;

    @Transactional
    public UserModel signup(String rawLoginId, String rawPassword, String rawName, LocalDate birthDate, String rawEmail) {
        LoginId loginId = new LoginId(rawLoginId);
        if (userRepository.existsByLoginId(loginId)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 존재하는 유저입니다.");
        }
        String encoded = passwordEncryptor.encode(rawPassword, birthDate);
        UserModel user = new UserModel(loginId, encoded, new UserName(rawName), birthDate, new Email(rawEmail));
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public UserModel authenticate(String rawLoginId, String rawPassword) {
        LoginId loginId;
        try {
            loginId = new LoginId(rawLoginId);
        } catch (CoreException e) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "인증 정보가 올바르지 않습니다.");
        }
        UserModel user = userRepository.findByLoginId(loginId)
            .orElseThrow(() -> new CoreException(ErrorType.UNAUTHORIZED, "인증 정보가 올바르지 않습니다."));
        if (!passwordEncryptor.matches(rawPassword, user.getEncodedPassword())) {
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
        if (!passwordEncryptor.matches(currentRawPassword, user.getEncodedPassword())) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "현재 비밀번호가 일치하지 않습니다.");
        }
        if (passwordEncryptor.matches(newRawPassword, user.getEncodedPassword())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "새 비밀번호는 현재 비밀번호와 같을 수 없습니다.");
        }
        String newEncoded = passwordEncryptor.encode(newRawPassword, user.getBirthDate());
        user.changePassword(newEncoded);
    }
}
