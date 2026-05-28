package com.loopers.application.brand;

import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Transactional(readOnly = true)
@RequiredArgsConstructor
@Component
public class BrandFacade {

    private final BrandService brandService;
    private final ProductService productService;

    @Transactional
    public BrandInfo create(String name, String description) {
        return BrandInfo.from(brandService.create(name, description));
    }

    /** 어드민용 — 삭제된 브랜드 포함 */
    public BrandInfo get(UUID id) {
        return BrandInfo.from(brandService.get(id));
    }

    /** 고객용 — 활성 브랜드만 */
    public BrandInfo getActive(UUID id) {
        return BrandInfo.from(brandService.getActive(id));
    }

    /** 어드민 목록 — 삭제된 브랜드 포함, 페이징 */
    public Page<BrandInfo> getList(Pageable pageable) {
        return brandService.getList(pageable).map(BrandInfo::from);
    }

    @Transactional
    public BrandInfo update(UUID id, String name, String description) {
        return BrandInfo.from(brandService.update(id, name, description));
    }

    /** 브랜드 소프트딜리트 + 산하 상품 cascade 소프트딜리트 */
    @Transactional
    public void delete(UUID id) {
        brandService.delete(id);
        productService.deleteByBrand(id);
    }
}
