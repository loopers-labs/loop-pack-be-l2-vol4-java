package com.loopers.application.brand;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class BrandFacade {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;

    private final BrandRepository brandRepository;
    private final ProductRepository productRepository;

    @Transactional
    public BrandInfo createBrand(String name, String description) {
        BrandModel brand = brandRepository.save(new BrandModel(name, description));
        return BrandInfo.from(brand);
    }

    @Transactional(readOnly = true)
    public BrandInfo getBrand(Long id) {
        return BrandInfo.from(getBrandModel(id));
    }

    @Transactional(readOnly = true)
    public List<BrandInfo> getBrands(Integer page, Integer size) {
        return paginate(brandRepository.findAll(), page, size).stream()
            .map(BrandInfo::from)
            .toList();
    }

    @Transactional
    public BrandInfo updateBrand(Long id, String name, String description) {
        BrandModel brand = getBrandModel(id);
        brand.update(name, description);
        return BrandInfo.from(brandRepository.save(brand));
    }

    @Transactional
    public void deleteBrand(Long id) {
        BrandModel brand = getBrandModel(id);
        brand.delete();
        brandRepository.save(brand);

        productRepository.findAllByBrandId(id).forEach(product -> {
            product.delete();
            productRepository.save(product);
        });
    }

    private BrandModel getBrandModel(Long id) {
        return brandRepository.find(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 브랜드를 찾을 수 없습니다."));
    }

    private List<BrandModel> paginate(List<BrandModel> brands, Integer page, Integer size) {
        int requestedPage = page == null ? DEFAULT_PAGE : page;
        int requestedSize = size == null ? DEFAULT_SIZE : size;
        validatePage(requestedPage, requestedSize);

        int fromIndex = requestedPage * requestedSize;
        if (fromIndex >= brands.size()) {
            return List.of();
        }

        int toIndex = Math.min(fromIndex + requestedSize, brands.size());
        return brands.subList(fromIndex, toIndex);
    }

    private void validatePage(int page, int size) {
        if (page < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "페이지 번호는 0 이상이어야 합니다.");
        }
        if (size < 1) {
            throw new CoreException(ErrorType.BAD_REQUEST, "페이지 크기는 1 이상이어야 합니다.");
        }
    }
}
