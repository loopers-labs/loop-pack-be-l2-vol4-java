package com.loopers.brand.application;

import com.loopers.brand.domain.BrandModel;
import com.loopers.product.application.ProductService;
import com.loopers.support.PageSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@RequiredArgsConstructor
@Service
@Transactional
public class BrandFacade {

    private final BrandService brandService;
    private final ProductService productService;

    public BrandInfo createBrand(String name, String description) {
        return BrandInfo.from(brandService.create(name, description));
    }

    @Transactional(readOnly = true)
    public BrandInfo getBrand(Long id) {
        return BrandInfo.from(brandService.get(id));
    }

    @Transactional(readOnly = true)
    public Page<BrandInfo> getBrands(int page, int size) {
        List<BrandInfo> infos =
            brandService.getAll().stream()
                .sorted(Comparator.comparing(BrandModel::getId).reversed())
                .map(BrandInfo::from)
                .toList();
        return PageSupport.paginate(infos, page, size);
    }

    public BrandInfo updateBrand(Long id, String name, String description) {
        return BrandInfo.from(brandService.update(id, name, description));
    }

    /** 브랜드를 논리 삭제하고, 해당 브랜드의 상품도 함께 논리 삭제한다. */
    public void deleteBrand(Long id) {
        brandService.delete(id);
        productService.deleteAllByBrandId(id);
    }
}
