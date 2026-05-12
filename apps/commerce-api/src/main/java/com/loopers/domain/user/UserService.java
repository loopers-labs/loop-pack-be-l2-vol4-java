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
    public UserModel registerUser(String id, String password, String name, LocalDate birthDate, String email) {
        if (userRepository.findByUserId(id).isPresent()) {
            throw new CoreException(ErrorType.USER_ALREADY_EXISTS, "[userId = " + id + "] 이미 가입된 로그인 ID 입니다.");
        }

        UserModel user = UserModel.create(id, password, name, birthDate, email, passwordEncryptor);
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public UserModel getUser(String userId, String password) {
        UserModel user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new CoreException(ErrorType.USER_NOT_FOUND, "[userId = " + userId + "] 사용자를 찾을 수 없습니다."));
        user.verifyPassword(password, passwordEncryptor);
        return user;
    }

    @Transactional
    public UserModel changePassword(String userId, String currentPassword, String newPassword) {
        UserModel user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new CoreException(ErrorType.USER_NOT_FOUND, "[userId = " + userId + "] 사용자를 찾을 수 없습니다."));
        user.changePassword(currentPassword, newPassword, passwordEncryptor);
        return user;
    }

}
