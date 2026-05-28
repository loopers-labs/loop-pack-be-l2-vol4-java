package com.loopers.member.interfaces.api;

import com.loopers.common.interfaces.api.ApiResponse;
import com.loopers.member.application.MemberFacade;
import com.loopers.member.application.MemberInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/users")
public class MemberController {

    private final MemberFacade memberFacade;

    @PostMapping
    public ApiResponse<MemberResponse> register(
        @RequestBody RegisterRequest request) {
        MemberInfo info = memberFacade.register(request.loginId(), request.password());
        return ApiResponse.success(MemberResponse.from(info));
    }

    @GetMapping("/me")
    public ApiResponse<MemberResponse> getMyInfo(
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @RequestHeader("X-Loopers-LoginPw") String loginPw) {
        Long memberId = memberFacade.authenticate(loginId, loginPw);
        MemberInfo info = memberFacade.getMyInfo(memberId);
        return ApiResponse.success(MemberResponse.from(info));
    }

    @PutMapping("/password")
    public ApiResponse<MemberResponse> changePassword(
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @RequestHeader("X-Loopers-LoginPw") String loginPw,
        @RequestBody ChangePasswordRequest request) {
        Long memberId = memberFacade.authenticate(loginId, loginPw);
        MemberInfo info =
            memberFacade.changePassword(memberId, request.currentPassword(), request.newPassword());
        return ApiResponse.success(MemberResponse.from(info));
    }
}
