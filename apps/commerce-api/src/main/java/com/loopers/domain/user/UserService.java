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
    public UserEntity signup(String userId, String password, String name, LocalDate birthDate, String email) {
        if (userRepository.findByUserId(userId).isPresent()) {
            throw new CoreException(ErrorType.CONFLICT, "이미 존재하는 사용자입니다.");
        }
        return userRepository.save(new UserEntity(userId, password, name, birthDate, email, passwordEncoder));
    }

    @Transactional(readOnly = true)
    public UserEntity getUser(String userId, String password) {
        UserEntity userModel = userRepository.findByUserId(userId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "없는 사용자입니다."));
        userModel.authenticate(password, passwordEncoder);
        return userModel;
    }

    @Transactional
    public UserEntity changePassword(String userId, String currentPassword, String newPassword) {
        UserEntity userModel = userRepository.findByUserId(userId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "없는 사용자입니다."));
        userModel.authenticate(currentPassword, passwordEncoder);
        userModel.changePassword(newPassword, passwordEncoder);
        return userRepository.save(userModel);
    }
}
