package com.loopers.application.brand;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class BrandFacade {

    private final BrandService brandService;

    @Transactional
    public BrandAdminInfo create(String name, String description) {
        BrandModel brand = brandService.create(name, description);
        return BrandAdminInfo.from(brand);
    }

    @Transactional
    public BrandAdminInfo update(Long id, String name, String description) {
        BrandModel brand = brandService.update(id, name, description);
        return BrandAdminInfo.from(brand);
    }

    @Transactional(readOnly = true)
    public BrandInfo getForCustomer(Long id) {
        BrandModel brand = brandService.getActive(id);
        return BrandInfo.from(brand);
    }

    @Transactional(readOnly = true)
    public BrandAdminInfo getForAdmin(Long id) {
        BrandModel brand = brandService.getBrand(id);
        return BrandAdminInfo.from(brand);
    }

    @Transactional(readOnly = true)
    public Page<BrandAdminInfo> list(Pageable pageable) {
        return brandService.getAll(pageable).map(BrandAdminInfo::from);
    }
}
