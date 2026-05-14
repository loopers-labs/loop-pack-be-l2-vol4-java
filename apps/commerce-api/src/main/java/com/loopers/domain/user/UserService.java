package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserModel register(String userid, String password, String name, String birthDay, String email) {
        if (userRepository.existsByUserid(userid)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 사용 중인 아이디입니다.");
        }
        UserModel.validatePassword(password, birthDay);
        UserModel user = new UserModel(userid, passwordEncoder.encode(password), name, birthDay, email);
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public UserModel getUser(String userid) {
        return userRepository.findByUserid(userid)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[userid = " + userid + "] 회원을 찾을 수 없습니다."));
    }

    @Transactional
    public UserModel changePassword(String userid, String newPassword) {
        UserModel user = userRepository.findByUserid(userid)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[userid = " + userid + "] 회원을 찾을 수 없습니다."));
        UserModel.validatePassword(newPassword, user.getBirthDay());
        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "현재 비밀번호는 사용할 수 없습니다.");
        }
        user.changePassword(passwordEncoder.encode(newPassword));
        return user;
    }
}
