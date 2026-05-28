package com.loopers.application.brand;

import com.loopers.domain.brand.BrandModel;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class BrandFacade {

    private final BrandService brandService;

    public BrandInfo create(String name) {
        BrandModel saved = brandService.create(new BrandModel(name));
        return BrandInfo.from(saved);
    }

    public BrandInfo getById(Long id) {
        return BrandInfo.from(brandService.getById(id));
    }

    public Page<BrandInfo> getAll(PageRequest pageRequest) {
        return brandService.getAll(pageRequest).map(BrandInfo::from);
    }

    public BrandInfo update(Long id, String name) {
        return BrandInfo.from(brandService.update(id, name));
    }

    public void delete(Long id) {
        brandService.delete(id);
    }
}
