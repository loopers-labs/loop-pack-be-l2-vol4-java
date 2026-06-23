package com.loopers.domain.brand;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class BrandService {

    private final BrandRepository brandRepository;

    @Transactional
    public BrandModel createBrand(String name, String description, String logoUrl) {
        BrandModel brand = new BrandModel(name, description, logoUrl);
        return brandRepository.save(brand);
    }

    @Transactional(readOnly = true)
    public BrandModel getBrand(Long id) {
        return brandRepository.find(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 브랜드를 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public List<BrandModel> getAllBrands() {
        return brandRepository.findAll();
    }

    /**
     * 여러 브랜드를 한 번의 쿼리로 조회해 id 기준 Map으로 반환한다.
     * 목록 조회 시 상품마다 브랜드를 개별 조회하던 N+1을 제거하기 위한 배치 조회용.
     */
    @Transactional(readOnly = true)
    public Map<Long, BrandModel> getBrands(Collection<Long> ids) {
        return brandRepository.findAllByIds(ids).stream()
            .collect(Collectors.toMap(BrandModel::getId, Function.identity()));
    }
}
