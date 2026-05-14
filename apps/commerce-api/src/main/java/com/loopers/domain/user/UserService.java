package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class UserService {

    private final UserRepository userRepository;

    public UserModel signup(String userId, String password, String name, LocalDate birthDate, String email) {
        // 1. 기존 user 확인
        Optional<UserModel> existUser = userRepository.findByUserId(userId);
        if (existUser.isPresent()) {
            throw new CoreException(ErrorType.CONFLICT, "이미 존재하는 사용자입니다.");
        }
        // 2. userModel 생성 및 저장 후 return
        return userRepository.save(new UserModel(userId, password, name, birthDate, email));
    }
    public UserModel getUser(String userId, String password) {
        // 1. 기존 user 확인
        Optional<UserModel> user = userRepository.findByUserId(userId);
        // 1-1. 기존 user가 존재하지 않다면 exception
        if (user.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "없는 사용자입니다.");
        }

        // 2. password가 일치여부 확인
        UserModel userModel = user.get();
        userModel.authenticate(password);
        // 3. userModel return
        return userModel;
    }
    public void changePassword(String userId, String currentPassword, String newPassword) {
        // 1. 기존 user Repository 확인
        Optional<UserModel> user = userRepository.findByUserId(userId);
        // 1-1. 기존 user가 존재하지 않다면 exception
        if (user.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "없는 사용자입니다.");
        }
        // 2. UserModel 에서 changePassword 처리
        UserModel userModel = user.get();
        userModel.changePassword(currentPassword, newPassword);
    }
}
