package com.loopers.tddstudy.controller;

import com.loopers.tddstudy.domain.User;
import com.loopers.tddstudy.dto.ChangePasswordRequest;
import com.loopers.tddstudy.dto.LoginRequest;
import com.loopers.tddstudy.dto.SignUpRequest;
import com.loopers.tddstudy.dto.UserInfoResponse;
import com.loopers.tddstudy.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/sign-up")
    public ResponseEntity<UserInfoResponse> signUp(@RequestBody SignUpRequest request) {
        User user = userService.signUp(request);
        return ResponseEntity.ok(new UserInfoResponse(user));
    }

    @PostMapping("/login")
    public ResponseEntity<UserInfoResponse> login(@RequestBody LoginRequest request) {
        User user = userService.login(request);
        return ResponseEntity.ok(new UserInfoResponse(user));
    }

    @GetMapping("/me")
    public ResponseEntity<UserInfoResponse> getUser(
            @RequestHeader(value = "X-Loopers-LoginId") String loginId,
            @RequestHeader(value = "X-Loopers-LoginPw") String loginPw) {
        User user = userService.login(new LoginRequest(loginId, loginPw));
        return ResponseEntity.ok(new UserInfoResponse(user));
    }

    @PatchMapping("/me/password")
    public ResponseEntity<String> changePassword(
            @RequestHeader(value = "X-Loopers-LoginId") String loginId,
            @RequestHeader(value = "X-Loopers-LoginPw") String loginPw,
            @RequestBody ChangePasswordRequest request){
        userService.changePassword(new LoginRequest(loginId,loginPw), request.newPassword());
        return  ResponseEntity.ok("비밀번호 변경 성공했습니다");
    }

}