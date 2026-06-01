package com.loopers.like.domain;

import com.loopers.support.Specification;

import java.util.Optional;

public class LikeNotDuplicateSpecification implements Specification<Optional<LikeModel>> {

    @Override
    public boolean isSatisfiedBy(Optional<LikeModel> existing) {
        return existing.isEmpty();
    }
}
