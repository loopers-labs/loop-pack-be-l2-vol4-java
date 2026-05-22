package com.loopers.service;

import com.loopers.dto.*;
import com.loopers.exception.ApiException;
import com.loopers.model.User;
import com.loopers.repository.InMemoryUserHistoryRepository;
import com.loopers.repository.InMemoryUserRepository;
import com.loopers.security.JwtUtil;
import com.loopers.util.PasswordRotateUtil;
import com.loopers.vo.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AuthService {

    private static final String AGENT_CODE = "Loopers";

    private final InMemoryUserRepository userRepository;
    private final InMemoryUserHistoryRepository historyRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthService(InMemoryUserRepository userRepository, InMemoryUserHistoryRepository historyRepository,
                       BCryptPasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.historyRepository = historyRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    public Map<String, Object> register(RegisterRequest req) {
        LoginId  loginId   = new LoginId(req.getLoginId());
        UserName name      = new UserName(req.getName());
        Email    email     = new Email(req.getEmail());
        Birthdate birthdate = new Birthdate(req.getBirthdate());
        Password password  = new Password(req.getPassword(), birthdate);

        if (userRepository.findByLoginId(loginId.getValue()) != null)
            throw new ApiException(HttpStatus.CONFLICT, "이미 사용 중인 아이디입니다.");
        if (userRepository.findByEmail(email.getValue()) != null)
            throw new ApiException(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다.");

        User user = new User();
        user.setLoginId(loginId.getValue());
        user.setName(name.getValue());
        user.setBirthdate(birthdate.getValue());
        user.setEmail(email.getValue());
        user.setPasswordHash(passwordEncoder.encode(password.getValue()));
        user.setDuressPasswordHash(
                req.getDuressPassword() != null && !req.getDuressPassword().isBlank()
                        ? passwordEncoder.encode(req.getDuressPassword()) : null);
        user.setRole(AGENT_CODE.equals(req.getReferral()) ? "agent" : "civilian");

        User saved = userRepository.save(user);
        return Map.of("token", jwtUtil.generateToken(saved), "user", UserResponse.from(saved));
    }

    public Map<String, Object> login(LoginRequest req) {
        if (req.getLoginId() == null || req.getPassword() == null || req.getLoginId().isBlank() || req.getPassword().isBlank())
            throw new ApiException(HttpStatus.BAD_REQUEST, "아이디와 비밀번호를 입력해주세요.");

        User user = userRepository.findByLoginId(req.getLoginId());
        if (user == null) throw new ApiException(HttpStatus.NOT_FOUND, "존재하지 않는 아이디입니다.");

        // 1) 과거 열쇠 함정
        for (String oldHash : user.getPasswordHistory()) {
            if (passwordEncoder.matches(req.getPassword(), oldHash)) {
                Map<String, Object> fakeUser = new LinkedHashMap<>();
                fakeUser.put("loginId", "fake_id");
                fakeUser.put("name", "GUEST");
                fakeUser.put("email", "unknown@textfix.kr");
                fakeUser.put("role", "civilian");
                fakeUser.put("isFake", true);
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("token", jwtUtil.generateFakeToken());
                result.put("user", fakeUser);
                result.put("trap", "old_key");
                return result;
            }
        }

        // 2) 자폭 비밀번호
        if (user.getDuressPasswordHash() != null && passwordEncoder.matches(req.getPassword(), user.getDuressPasswordHash())) {
            userRepository.deleteById(user.getId());
            return Map.of("selfDestruct", true);
        }

        // 3) 정상 로그인
        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash()))
            throw new ApiException(HttpStatus.UNAUTHORIZED, "비밀번호가 올바르지 않습니다.");

        // 4) 요원 비밀번호 자동 변경 (각 숫자 +1 % 10)
        if ("agent".equals(user.getRole())) {
            String newHash = passwordEncoder.encode(PasswordRotateUtil.rotateDigits(req.getPassword()));
            userRepository.addPasswordHistory(user.getId(), user.getPasswordHash());
            historyRepository.record(user.getId(), "password", user.getPasswordHash(), newHash);
            user.setPasswordHash(newHash);
            userRepository.save(user);
        }

        return Map.of("token", jwtUtil.generateToken(user), "user", UserResponse.from(user));
    }

    public UserResponse getMe(int userId) {
        User user = userRepository.findById(userId);
        if (user == null) throw new ApiException(HttpStatus.NOT_FOUND, "유저를 찾을 수 없습니다.");
        return UserResponse.from(user);
    }

    public UserResponse updateProfile(int userId, UpdateProfileRequest req) {
        User user = userRepository.findById(userId);
        if (user == null) throw new ApiException(HttpStatus.NOT_FOUND, "유저를 찾을 수 없습니다.");
        if (req.getName() != null && !req.getName().isBlank() && !req.getName().equals(user.getName())) {
            historyRepository.record(userId, "name", user.getName(), req.getName());
            user.setName(req.getName());
        }
        if (req.getEmail() != null && !req.getEmail().isBlank() && !req.getEmail().equals(user.getEmail())) {
            historyRepository.record(userId, "email", user.getEmail(), req.getEmail());
            user.setEmail(req.getEmail());
        }
        userRepository.save(user);
        return UserResponse.from(user);
    }

    public void changePassword(int userId, ChangePasswordRequest req) {
        if (req.getCurrentPassword() == null || req.getNewPassword() == null || req.getCurrentPassword().isBlank() || req.getNewPassword().isBlank())
            throw new ApiException(HttpStatus.BAD_REQUEST, "현재 비밀번호와 새 비밀번호를 입력해주세요.");
        User user = userRepository.findById(userId);
        if (user == null) throw new ApiException(HttpStatus.NOT_FOUND, "유저를 찾을 수 없습니다.");
        if (!passwordEncoder.matches(req.getCurrentPassword(), user.getPasswordHash()))
            throw new ApiException(HttpStatus.UNAUTHORIZED, "현재 비밀번호가 올바르지 않습니다.");
        String newHash = passwordEncoder.encode(req.getNewPassword());
        userRepository.addPasswordHistory(userId, user.getPasswordHash());
        historyRepository.record(userId, "password", user.getPasswordHash(), newHash);
        user.setPasswordHash(newHash);
        userRepository.save(user);
    }

    public Map<String, Object> validatePassword(String password) {
        if (password == null || password.isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST, "password 필드가 필요합니다.");
        List<String> errors = Password.validate(password, null);
        Map<String, Object> result = new LinkedHashMap<>();
        if (!errors.isEmpty()) { result.put("valid", false); result.put("errors", errors); }
        else result.put("valid", true);
        return result;
    }

    public void deleteAccount(int userId) {
        if (!userRepository.deleteById(userId)) throw new ApiException(HttpStatus.NOT_FOUND, "유저를 찾을 수 없습니다.");
    }

    public List<UserHistoryResponse> getMyHistory(int userId) {
        return historyRepository.findByUserId(userId).stream().map(UserHistoryResponse::from).collect(Collectors.toList());
    }
}
