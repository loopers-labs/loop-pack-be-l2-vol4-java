package com.loopers.domain.user;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@RequiredArgsConstructor
@Component
public class UserService {

    private final UserRepository userRepository;

    public UserModel signup(String userId, String password, String name, LocalDate birthDate, String email) {
        // 1. 기존 user 확인
        // 2. user 생성
        // 3. return
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
