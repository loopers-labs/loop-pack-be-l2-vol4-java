package com.loopers.domain.brand;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
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
    public List<BrandModel> getAllByIdIn(Collection<Long> ids) {
        return brandRepository.findAllByIdIn(ids);
    }
}
