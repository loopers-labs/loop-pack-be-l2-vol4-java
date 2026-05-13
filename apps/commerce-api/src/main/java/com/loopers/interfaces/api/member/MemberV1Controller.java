package com.loopers.interfaces.api.member;

import com.loopers.application.member.MemberFacade;
import com.loopers.application.member.MemberInfo;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/members")
public class MemberV1Controller {

    private final MemberFacade memberFacade;

    @PostMapping
    public ApiResponse<MemberV1Dto.MemberResponse> join(@RequestBody MemberV1Dto.MemberJoinRequest request) {
        MemberInfo memberInfo = memberFacade.join(request.toCommand());
        return ApiResponse.success(MemberV1Dto.MemberResponse.from(memberInfo));
    }
}
