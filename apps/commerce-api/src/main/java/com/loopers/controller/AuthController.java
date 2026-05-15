package com.loopers.controller;

import com.loopers.dto.*;
import com.loopers.security.TokenBlacklist;
import com.loopers.security.UserPrincipal;
import com.loopers.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final TokenBlacklist tokenBlacklist;

    public AuthController(AuthService authService, TokenBlacklist tokenBlacklist) {
        this.authService = authService;
        this.tokenBlacklist = tokenBlacklist;
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody RegisterRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(req));
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequest req) {
        return ResponseEntity.ok(authService.login(req));
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getMe(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(authService.getMe(principal.getId()));
    }

    @PatchMapping("/me")
    public ResponseEntity<UserResponse> updateProfile(@AuthenticationPrincipal UserPrincipal principal,
                                                      @RequestBody UpdateProfileRequest req) {
        return ResponseEntity.ok(authService.updateProfile(principal.getId(), req));
    }

    @PatchMapping("/me/password")
    public ResponseEntity<Map<String, String>> changePassword(@AuthenticationPrincipal UserPrincipal principal,
                                                              @RequestBody ChangePasswordRequest req) {
        authService.changePassword(principal.getId(), req);
        return ResponseEntity.ok(Map.of("message", "비밀번호가 변경되었습니다."));
    }

    @PostMapping("/validate-password")
    public ResponseEntity<Map<String, Object>> validatePassword(@RequestBody ValidatePasswordRequest req) {
        Map<String, Object> result = authService.validatePassword(req.getPassword());
        boolean valid = (boolean) result.get("valid");
        return ResponseEntity.status(valid ? HttpStatus.OK : HttpStatus.UNPROCESSABLE_ENTITY).body(result);
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(@RequestHeader("Authorization") String authHeader) {
        tokenBlacklist.add(authHeader.substring(7));
        return ResponseEntity.ok(Map.of("message", "로그아웃 되었습니다."));
    }

    @DeleteMapping("/me")
    public ResponseEntity<Map<String, String>> deleteAccount(@AuthenticationPrincipal UserPrincipal principal) {
        authService.deleteAccount(principal.getId());
        return ResponseEntity.ok(Map.of("message", "계정이 삭제되었습니다."));
    }

    @GetMapping("/me/history")
    public ResponseEntity<Map<String, Object>> getMyHistory(@AuthenticationPrincipal UserPrincipal principal) {
        List<UserHistoryResponse> history = authService.getMyHistory(principal.getId());
        return ResponseEntity.ok(Map.of("history", history));
    }
}
