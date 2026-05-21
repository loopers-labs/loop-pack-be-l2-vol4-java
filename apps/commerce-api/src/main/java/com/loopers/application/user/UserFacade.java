package com.loopers.application.user;

import com.loopers.domain.user.User;
import com.loopers.domain.user.UserRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@RequiredArgsConstructor
@Component
public class UserFacade {

    private final UserRepository userRepository;

    @Transactional
    public UserInfo signUp(SignUpCommand command) {
        if (userRepository.findByLoginId(command.loginId()).isPresent()) {
            throw new CoreException(ErrorType.CONFLICT, "이미 가입된 로그인 ID 입니다.");
        }

        User user = User.create(
            command.loginId(),
            command.password(),
            command.name(),
            command.birthDate(),
            command.email()
        );
        user.encryptPassword(encrypt(command.password()));

        return UserInfo.from(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public UserInfo getMyInfo(String loginId) {
        return UserInfo.from(findByUserId(loginId));
    }

    @Transactional
    public void changePassword(String loginId, ChangePasswordCommand command) {
        User user = findByUserId(loginId);

        String encryptedCurrent = encrypt(command.currentPassword());
        if (!user.getPassword().equals(encryptedCurrent)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "현재 비밀번호가 일치하지 않습니다.");
        }

        user.changePassword(command.currentPassword(), command.newPassword());
        user.encryptPassword(encrypt(command.newPassword()));
    }

    public User findByUserId(String loginId) {
        return userRepository.findByLoginId(loginId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 회원입니다."));
    }

    @Transactional(readOnly = true)
    public void authenticate(String loginId, String password) {
        User user = findByUserId(loginId);
        if (!user.getPassword().equals(encrypt(password))) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호가 일치하지 않습니다.");
        }
    }

    private String encrypt(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encrypted = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(encrypted);
        } catch (NoSuchAlgorithmException e) {
            throw new CoreException(ErrorType.INTERNAL_ERROR, "비밀번호 암호화에 실패했습니다.");
        }
    }
}
