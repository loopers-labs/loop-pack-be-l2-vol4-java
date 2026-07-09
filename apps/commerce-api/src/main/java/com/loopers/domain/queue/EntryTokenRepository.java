package com.loopers.domain.queue;

import java.util.Optional;

public interface EntryTokenRepository {
    String issue(Long userId);
    Optional<String> find(Long userId);
    void delete(Long userId);
}
