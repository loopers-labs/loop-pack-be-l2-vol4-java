package com.loopers.interfaces.api.member;

import com.loopers.application.member.MemberFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/members")
public class MemberController {

    private final MemberFacade memberFacade;

    @PostMapping("/signup")
    public void signUp(@RequestBody MemberRequest.SignUp request) {
        memberFacade.signUp(request);
    }
}
