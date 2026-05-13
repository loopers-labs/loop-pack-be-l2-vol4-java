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
            throw new CoreException(ErrorType.BAD_REQUEST, "이미 존재하는 사용자입니다.");
        }
        // 2. userModel 생성 및 저장
        return userRepository.save(new UserModel(userId, password, name, birthDate, email));
    }
    public UserModel getUser(String userId, String password) {
        // 1. 기존 user 확인
        // 1-1. 기존 user가 존재하지 않다면 exception
        // 2. password가 일치하지 않는 경우 exception
        // 3. userModel return
    }
    public UserModel changePassword(String userId, String currentPassword, String newPassword) {
        // 1. 기존 user Repository 확인
        // 1-1. 기존 user가 없다면 Exception 처리
        // 2. UserModel 에서 changePassword 처리
    }
}
