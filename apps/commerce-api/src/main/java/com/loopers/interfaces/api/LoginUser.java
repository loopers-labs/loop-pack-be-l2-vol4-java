package com.loopers.interfaces.api;

// 헤더 인증을 통과한 유저 정보. 컨트롤러 파라미터로 주입된다 (@CurrentUser LoginUser)
public record LoginUser(Long id, String loginId) {}
