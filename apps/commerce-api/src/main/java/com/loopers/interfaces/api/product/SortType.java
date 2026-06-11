package com.loopers.interfaces.api.product;

import org.springframework.data.domain.Sort;

import static org.springframework.data.domain.Sort.Direction.ASC;
import static org.springframework.data.domain.Sort.Direction.DESC;

public enum SortType {
    LATEST {
        @Override
        public Sort toSort() { return Sort.by(DESC, "createdAt"); }
    },
    PRICE_ASC {
        @Override
        public Sort toSort() { return Sort.by(ASC, "price"); }
    },
    LIKES_DESC {
        @Override
        public Sort toSort() { return Sort.by(DESC, "likeCount"); }
    };

    public abstract Sort toSort();
}
