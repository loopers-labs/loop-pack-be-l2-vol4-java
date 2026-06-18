package com.loopers.tddstudy.infrastructure.product;

import com.loopers.tddstudy.domain.product.Product;
import com.loopers.tddstudy.domain.product.ProductRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class ProductRepositoryImpl implements ProductRepository {

    private final ProductJpaRepository jpaRepository;

    public ProductRepositoryImpl(ProductJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Product save(Product product) {
        return jpaRepository.save(product);
    }

    @Override
    public Optional<Product> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public Optional<Product> findByIdWithLock(Long id) {
        return jpaRepository.findWithLockById(id);
    }

    @Override
    public List<Product> findAll() {
        return jpaRepository.findAll();
    }

    @Override
    public List<Product> findAllByBrandId(Long brandId) {
        return jpaRepository.findAllByBrandId(brandId);
    }

    @Override
    public List<Product> findAllByFilter(Long brandId, String sort) {
        boolean likeSort = "likes_desc".equals(sort);

        if (brandId != null && likeSort) {
            return jpaRepository.findAllByBrandIdOrderByLikeCountDesc(brandId);
        }
        if (brandId != null) {
            return jpaRepository.findAllByBrandId(brandId);
        }
        if (likeSort) {
            return jpaRepository.findAllByOrderByLikeCountDesc();
        }
        return jpaRepository.findAll();
    }

    @Override
    public List<Product> saveAll(List<Product> products) {
        return jpaRepository.saveAll(products);
    }
        //탑10 캐시 테스트용
    @Override
    public List<Product> findTop10ByOrderByLikeCountDesc() {
        return jpaRepository.findTop10ByOrderByLikeCountDesc();
    }



}
