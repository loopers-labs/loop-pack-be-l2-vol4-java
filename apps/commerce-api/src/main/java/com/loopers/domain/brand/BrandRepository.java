package com.loopers.domain.brand;

import org.springframework.data.domain.Page;

public interface BrandRepository {

    BrandModel save(BrandModel brand);

    boolean existsActiveByName(String name);

    boolean existsActiveByNameAndIdNot(String name, Long id);

    BrandModel getActiveById(Long id);

    Page<BrandModel> findActiveByPage(int page, int size);
}
