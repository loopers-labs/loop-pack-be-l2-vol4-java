package com.loopers.infrastructure.queue;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.queue.QueueAdmissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@Component
public class QueueAdmissionRepositoryImpl implements QueueAdmissionRepository {

    private static final DefaultRedisScript<List> ADMIT_BATCH_SCRIPT = loadScript();

    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private final RedisTemplate<String, String> masterRedisTemplate;

    @Override
    @SuppressWarnings("unchecked")
    public List<AdmittedEntry> admitBatch(int count, Duration tokenTtl) {
        if (count <= 0) {
            return List.of();
        }

        List<String> scriptArgs = new ArrayList<>();
        scriptArgs.add(String.valueOf(count));
        scriptArgs.add(String.valueOf(tokenTtl.toSeconds()));
        scriptArgs.add(QueueRedisKeys.ENTRY_TOKEN_KEY_PREFIX);
        for (int i = 0; i < count; i++) {
            scriptArgs.add(UUID.randomUUID().toString());
        }

        List<String> raw = masterRedisTemplate.execute(
                ADMIT_BATCH_SCRIPT,
                List.of(QueueRedisKeys.WAITING_QUEUE_KEY),
                scriptArgs.toArray()
        );

        List<AdmittedEntry> admitted = new ArrayList<>();
        for (int i = 0; i < raw.size(); i += 2) {
            Long userId = Long.parseLong(raw.get(i));
            String token = raw.get(i + 1);
            admitted.add(new AdmittedEntry(userId, token));
        }
        return admitted;
    }

    private static DefaultRedisScript<List> loadScript() {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("scripts/admit-batch.lua"));
        script.setResultType(List.class);
        return script;
    }
}
