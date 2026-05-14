package com.loopers.interfaces.api.member;

import com.loopers.domain.member.MemberModel;
import com.loopers.domain.member.MemberService;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/member")
public class MemberV1Controller {

    private final MemberService memberService;

    @PostMapping
    public ApiResponse<MemberV1Dto.CreateMemberResponse> createMember(@RequestBody MemberV1Dto.CreateMemberRequest request) {
        MemberModel member = memberService.registerMember(
                request.userId(), request.password(), request.email(), request.userName(), request.birthDate()
        );
        return ApiResponse.success(new MemberV1Dto.CreateMemberResponse(member.getUserId(), member.getUsername()));
    }

    @GetMapping("/{id}")
    public ApiResponse<MemberV1Dto.MemberInfoResponse> getMember(@PathVariable Long id) {
        MemberModel member = memberService.getMember(id);
        return ApiResponse.success(new MemberV1Dto.MemberInfoResponse(
                member.getUserId(), member.getMaskedUsername(), member.getBirthDate(), member.getEmail()
        ));
    }

    @PatchMapping("/{id}")
    public ApiResponse<Object> updatePassword(@PathVariable Long id, @RequestBody MemberV1Dto.UpdatePasswordRequest request) {
        memberService.updatePassword(id, request.currentPassword(), request.newPassword());
        return ApiResponse.success();
    }
}
