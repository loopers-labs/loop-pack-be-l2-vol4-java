package com.loopers.application.ranking;

import java.util.Collection;
import java.util.Set;

public interface ActiveProductRepository {
    Set<Long> findActiveProductIds(Collection<Long> productIds);
}
