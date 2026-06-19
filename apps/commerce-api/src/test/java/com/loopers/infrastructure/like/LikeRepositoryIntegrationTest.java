package com.loopers.infrastructure.like;

import com.loopers.domain.like.LikeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@SpringBootTest
class LikeRepositoryIntegrationTest extends LikeRepositoryContractTest {

    @Autowired
    private LikeRepository likeRepository;

    @Override
    LikeRepository repository() {
        return likeRepository;
    }
}
