package com.loopers.tddstudy.service;

import com.loopers.tddstudy.domain.User;
import com.loopers.tddstudy.dto.LoginRequest;
import com.loopers.tddstudy.dto.SignUpRequest;
import com.loopers.tddstudy.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

@Service
public class UserService {
    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }
    // 회원가입 서비스
    public User signUp(SignUpRequest request) {
        if (userRepository.existsByLoginId(request.loginId())) {
            throw new IllegalArgumentException("이미 사용중인 로그인 ID입니다.");
        }
        User user = new User(request.loginId(), request.loginPw(), request.name(), request.birthDate(), request.email());
        return userRepository.save(user);
    }
    
    //로그인 서비스
    public User login(LoginRequest request) {
        User user = findUserByLoginId(request.loginId());
        if (!user.matchesPassword(request.loginPw())) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }
        return user;
    }

    //비밀번호 변경 서비스
    @Transactional
    public void changePassword(LoginRequest loginRequest, String newPassword){
       User user = findUserByLoginId(loginRequest.loginId());
        user.changePassword(newPassword,loginRequest.loginPw());
    }

    //유저 조회 공통 메서드
    private  User findUserByLoginId(String loginId){
        return userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));
    }


}
