package com.loopers.application.brand;

import com.loopers.application.product.ProductService;
import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class BrandService {

    private final BrandRepository brandRepository;
    private final ProductService productService;

    @Transactional
    public BrandInfo create(BrandCreateCommand command) {
        if (brandRepository.existsActiveByName(command.name())) {
            throw new CoreException(ErrorType.CONFLICT, "이미 존재하는 브랜드명입니다.");
        }
        return BrandInfo.from(brandRepository.save(command.toDomain()));
    }

    @Transactional(readOnly = true)
    public BrandInfo getById(Long id) {
        BrandModel brand = brandRepository.findById(id)
            .filter(b -> !b.isDeleted())
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다."));
        return BrandInfo.from(brand);
    }

    @Transactional(readOnly = true)
    public Page<BrandInfo> getAll(Pageable pageable) {
        return brandRepository.findAllActive(pageable).map(BrandInfo::from);
    }

    @Transactional
    public BrandInfo update(Long id, BrandUpdateCommand command) {
        BrandModel brand = brandRepository.findById(id)
            .filter(b -> !b.isDeleted())
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다."));
        if (!brand.getName().equals(command.name()) && brandRepository.existsActiveByName(command.name())) {
            throw new CoreException(ErrorType.CONFLICT, "이미 존재하는 브랜드명입니다.");
        }
        brand.update(command.name(), command.description());
        return BrandInfo.from(brandRepository.save(brand));
    }

    @Transactional
    public void delete(Long id) {
        BrandModel brand = brandRepository.findById(id)
            .filter(b -> !b.isDeleted())
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다."));
        brand.delete();
        brandRepository.save(brand);
        productService.deleteAllByBrandId(id);
    }
}
