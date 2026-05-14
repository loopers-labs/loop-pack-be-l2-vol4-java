package com.loopers.interfaces.api.member;

import com.loopers.application.member.MemberFacade;
import com.loopers.application.member.MemberInfo;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/members")
public class MemberController {

    private final MemberFacade memberFacade;

    @PostMapping("/signup")
    public ApiResponse<Void> signUp(@RequestBody MemberV1Dto.SignUpRequest request) {
        memberFacade.signUp(
                request.loginId(),
                request.password(),
                request.name(),
                request.birthDate(),
                request.email()
        );
        return ApiResponse.success(null);
    }

    @GetMapping("/me")
    public ApiResponse<MemberV1Dto.MemberResponse> getMyInfo(
            @RequestHeader("X-Loopers-LoginId") String loginId,
            @RequestHeader("X-Loopers-LoginPw") String password
    ) {
        MemberInfo info = memberFacade.getMyInfo(loginId, password);
        MemberV1Dto.MemberResponse response = MemberV1Dto.MemberResponse.from(info);
        return ApiResponse.success(response);
    }

    @PatchMapping("/me/password")
    public ApiResponse<Void> updatePassword(
            @RequestHeader("X-Loopers-LoginId") String loginId,
            @RequestHeader("X-Loopers-LoginPw") String password,
            @RequestBody MemberV1Dto.UpdatePasswordRequest request
    ) {
        memberFacade.updatePassword(
                loginId,
                password,
                request.oldPassword(),
                request.newPassword()
        );
        return ApiResponse.success(null);
    }
}
