package com.loopers.domain.brand;

public interface BrandRepository {

    BrandModel save(BrandModel brand);

    boolean existsActiveByName(String name);
}
