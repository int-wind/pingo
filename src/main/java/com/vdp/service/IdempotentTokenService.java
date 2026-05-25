package com.vdp.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.Duration;
import java.util.Collections;
import java.util.UUID;

import static com.vdp.utils.RedisConstants.ORDER_IDEMPOTENT_KEY;

/**
 * 秒杀下单幂等：先发 Token（单次有效），提交时校验用户归属并删除，防止重复提交与乱序重试。
 */
@Service
public class IdempotentTokenService {

    private static final Duration TOKEN_TTL = Duration.ofMinutes(5);

    private static final DefaultRedisScript<Long> CONSUME_SCRIPT = new DefaultRedisScript<>();
    static {
        CONSUME_SCRIPT.setResultType(Long.class);
        CONSUME_SCRIPT.setScriptText(
                "local v = redis.call('get', KEYS[1]); "
                        + "if v == false then return 0 end; "
                        + "if v ~= ARGV[1] then return -1 end; "
                        + "redis.call('del', KEYS[1]); return 1");
    }

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 签发幂等 Token（绑定当前用户，短时有效）
     */
    public String createToken(Long userId) {
        String token = UUID.randomUUID().toString().replace("-", "");
        String key = ORDER_IDEMPOTENT_KEY + token;
        Boolean ok = stringRedisTemplate.opsForValue().setIfAbsent(key, userId.toString(), TOKEN_TTL);
        if (Boolean.TRUE.equals(ok)) {
            return token;
        }
        return createToken(userId);
    }

    /**
     * 校验 Token 是否属于该用户并原子删除；重复提交第二次会失败
     *
     * @return 1 成功消费；-1 Token 归属不匹配；0 Token 不存在或已使用
     */
    public long validateAndConsume(Long userId, String token) {
        if (token == null || token.isEmpty()) {
            return 0;
        }
        String key = ORDER_IDEMPOTENT_KEY + token;
        Long r = stringRedisTemplate.execute(CONSUME_SCRIPT, Collections.singletonList(key), userId.toString());
        return r != null ? r : 0;
    }
}
