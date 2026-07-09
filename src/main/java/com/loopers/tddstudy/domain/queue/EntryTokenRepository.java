package com.loopers.tddstudy.domain.queue;

public interface EntryTokenRepository {
    void issue(Long userId, String token, long ttlSeconds);  // SET EX
    String find(Long userId);                                 // GET (없으면 null)
    boolean consume(Long userId, String token);               // 검증+삭제 원자적(Lua)
}
