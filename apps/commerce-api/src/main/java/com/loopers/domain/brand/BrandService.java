package com.loopers.domain.brand;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

@Component
@RequiredArgsConstructor
public class BrandService {

    private final BrandRepository brandRepository;

    @Transactional
    public BrandModel create(String name, String description) {
        if (brandRepository.existsByName(name)) {
            throw new CoreException(ErrorType.DUPLICATE_BRAND_NAME,
                "[name = " + name + "] 이미 존재하는 브랜드명입니다.");
        }
        return brandRepository.save(new BrandModel(name, description));
    }

    @Transactional
    public BrandModel update(Long id, String name, String description) {
        BrandModel brand = brandRepository.findActive(id)
            .orElseThrow(() -> new CoreException(ErrorType.BRAND_NOT_FOUND,
                "[id = " + id + "] 브랜드를 찾을 수 없습니다."));

        if (name != null) {
            if (brandRepository.existsByNameAndIdNot(name, id)) {
                throw new CoreException(ErrorType.DUPLICATE_BRAND_NAME,
                    "[name = " + name + "] 이미 존재하는 브랜드명입니다.");
            }
            brand.changeName(name);
        }
        if (description != null) {
            brand.changeDescription(description);
        }
        return brand;
    }

    @Transactional(readOnly = true)
    public BrandModel getActive(Long id) {
        return brandRepository.findActive(id)
            .orElseThrow(() -> new CoreException(ErrorType.BRAND_NOT_FOUND,
                "[id = " + id + "] 브랜드를 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public BrandModel getBrand(Long id) {
        return brandRepository.find(id)
            .orElseThrow(() -> new CoreException(ErrorType.BRAND_NOT_FOUND,
                "[id = " + id + "] 브랜드를 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public Page<BrandModel> getAll(Pageable pageable) {
        return brandRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public List<BrandModel> getAllByIdIn(Collection<Long> ids) {
        return brandRepository.findAllByIdIn(ids);
    }
}
