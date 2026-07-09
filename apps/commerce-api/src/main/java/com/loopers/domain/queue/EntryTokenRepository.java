package com.loopers.domain.queue;

import java.util.Optional;

public interface EntryTokenRepository {

    Optional<String> find(Long userId);

    void delete(Long userId);
}
