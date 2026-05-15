package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

@RequiredArgsConstructor
@Service
public class UserService {

    private static final Pattern PASSWORD_PATTERN =
        Pattern.compile("^[a-zA-Z0-9!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?]{8,16}$");
    private static final DateTimeFormatter BIRTH_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserModel register(UserRegisterCommand command) {
        if (userRepository.existsByLoginId(command.loginId())) {
            throw new CoreException(ErrorType.CONFLICT, "이미 존재하는 loginId 입니다.");
        }

        validatePassword(command.password(), command.birthDate());

        String encodedPassword = passwordEncoder.encode(command.password());
        UserModel user = new UserModel(
            command.loginId(), encodedPassword, command.name(), command.birthDate(), command.email()
        );
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public UserModel getUser(String loginId) {
        return userRepository.findByLoginId(loginId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 회원입니다."));
    }

    @Transactional(readOnly = true)
    public UserModel authenticate(String loginId, String rawPassword) {
        UserModel user = userRepository.findByLoginId(loginId)
            .orElseThrow(() -> new CoreException(ErrorType.UNAUTHORIZED, "인증에 실패했습니다."));

        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "인증에 실패했습니다.");
        }
        return user;
    }

    @Transactional
    public void changePassword(String loginId, String currentPassword, String newPassword) {
        UserModel user = getUser(loginId);

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "현재 비밀번호가 일치하지 않습니다.");
        }

        validatePassword(newPassword, user.getBirthDate());

        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "새 비밀번호는 현재 비밀번호와 달라야 합니다.");
        }
        user.updatePassword(passwordEncoder.encode(newPassword));
    }

    private void validatePassword(String password, LocalDate birthDate) {
        if (password == null || !PASSWORD_PATTERN.matcher(password).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 영문 대소문자, 숫자, 특수문자로만 이루어진 8~16자여야 합니다.");
        }
        if (password.contains(birthDate.format(BIRTH_DATE_FORMATTER))) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호에 생년월일이 포함될 수 없습니다.");
        }
    }
}
