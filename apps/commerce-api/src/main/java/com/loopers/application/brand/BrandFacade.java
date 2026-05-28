package com.loopers.application.brand;

import com.loopers.domain.brand.BrandService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.UUID;

@RequiredArgsConstructor
@Component
public class BrandFacade {

    private final BrandService brandService;

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

    public BrandInfo update(UUID id, String name, String description) {
        return BrandInfo.from(brandService.update(id, name, description));
    }

    public void delete(UUID id) {
        brandService.delete(id);
    }
}
