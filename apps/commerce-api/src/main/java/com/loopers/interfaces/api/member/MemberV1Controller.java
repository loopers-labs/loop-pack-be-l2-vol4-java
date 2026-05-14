package com.loopers.interfaces.api.member;

import com.loopers.application.member.MemberFacade;
import com.loopers.application.member.MemberInfo;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/member")
public class MemberV1Controller {

    private final MemberFacade memberFacade;

    @PostMapping
    public ApiResponse<MemberV1Dto.CreateMemberResponse> createMember(@RequestBody MemberV1Dto.CreateMemberRequest request) {
        MemberInfo info = memberFacade.registerMember(
                request.userId(), request.password(), request.email(), request.userName(), request.birthDate()
        );
        return ApiResponse.success(MemberV1Dto.CreateMemberResponse.from(info));
    }

    @GetMapping("/{id}")
    public ApiResponse<MemberV1Dto.MemberInfoResponse> getMember(@PathVariable Long id) {
        MemberInfo info = memberFacade.getMember(id);
        return ApiResponse.success(MemberV1Dto.MemberInfoResponse.from(info));
    }

    @PatchMapping("/{id}")
    public ApiResponse<Object> updatePassword(@PathVariable Long id, @RequestBody MemberV1Dto.UpdatePasswordRequest request) {
        memberFacade.updatePassword(id, request.currentPassword(), request.newPassword());
        return ApiResponse.success();
    }
}
