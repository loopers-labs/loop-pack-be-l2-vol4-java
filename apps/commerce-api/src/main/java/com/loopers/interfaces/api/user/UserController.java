package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserFacade;
import com.loopers.application.user.UserInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.user.dto.SignUpRequest;
import com.loopers.interfaces.api.user.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserFacade userFacade;

    @PostMapping
    public ResponseEntity<ApiResponse<UserResponse>> signUp(@RequestBody SignUpRequest request) {
        UserInfo userInfo = userFacade.signUp(request.toCommand());
        UserResponse response = UserResponse.from(userInfo);

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }
}
