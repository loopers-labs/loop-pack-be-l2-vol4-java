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
    public UserModel signup(String userId, String password, String name, LocalDate birthDate, String email) {
        if (userRepository.findByUserId(userId).isPresent()) {
            throw new CoreException(ErrorType.CONFLICT, "이미 존재하는 사용자입니다.");
        }
        return userRepository.save(new UserModel(userId, password, name, birthDate, email));
    }

    @Transactional(readOnly = true)
    public UserModel getUser(String userId, String password) {
        UserModel userModel = userRepository.findByUserId(userId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "없는 사용자입니다."));
        userModel.authenticate(password);
        return userModel;
    }

    @Transactional
    public UserModel changePassword(String userId, String currentPassword, String newPassword) {
        UserModel userModel = userRepository.findByUserId(userId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "없는 사용자입니다."));
        userModel.changePassword(currentPassword, newPassword);
        return userModel;
    }
}
