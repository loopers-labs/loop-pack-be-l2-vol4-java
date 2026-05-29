package com.loopers.domain.user;

import com.loopers.domain.user.enums.UserRole;
import com.loopers.domain.user.vo.BirthDay;
import com.loopers.domain.user.vo.Email;
import com.loopers.domain.user.vo.Name;
import com.loopers.domain.user.vo.Password;
import com.loopers.domain.user.vo.RawPassword;
import com.loopers.domain.user.vo.UserId;
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
    public UserModel register(UserId userid, RawPassword rawPassword, Name name, BirthDay birthDay, Email email) {
        if (userRepository.existsByUserId(userid)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 사용 중인 아이디입니다.");
        }

        PasswordPolicy.validatePasswordNotContainBirthDay(rawPassword, birthDay);

        UserModel user = new UserModel(
                userid,
                new Password(passwordEncoder.encode(rawPassword.getValue())),
                name,
                birthDay,
                email,
                UserRole.USER
        );
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public UserModel getUser(UserId userid) {
        return userRepository.findByUserId(userid)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[userid = " + userid.getValue() + "] 회원을 찾을 수 없습니다."));
    }

    @Transactional
    public UserModel changePassword(UserId userid, RawPassword newRawPassword) {
        UserModel user = getUser(userid);

        PasswordPolicy.validatePasswordNotContainBirthDay(newRawPassword, user.getBirthDay());
        PasswordPolicy.validateNotSamePassword(passwordEncoder.matches(newRawPassword.getValue(), user.getPassword().getValue()));

        user.changePassword(new Password(passwordEncoder.encode(newRawPassword.getValue())));
        return user;
    }
}
