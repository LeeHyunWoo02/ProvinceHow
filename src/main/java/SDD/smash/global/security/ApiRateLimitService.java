package SDD.smash.global.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ApiRateLimitService {

    private static final String ZSET_PREFIX = "ip_rate";
    private static final String LOCK_PREFIX = "ip_lock";

    @Value("${ratelimit.window-seconds:10}")
    private long WINDOW_SECONDS; // 윈도우 크기(초)

    @Value("${ratelimit.allowed-count:30}")
    private long ALLOWED_COUNT;   // 허용할 횟수

    @Value("${ratelimit.lock-ttl-ms:60000}")
    private long LOCK_TTL_MS; // 락 TTL(ms)

    private static final String LUA = ""
            + "local zsetKey   = KEYS[1]\n"
            + "local lockKey   = KEYS[2]\n"
            + "local now       = tonumber(ARGV[1])\n"
            + "local windowSec = tonumber(ARGV[2])\n"
            + "local limit     = tonumber(ARGV[3])\n"
            + "local lockTtl   = tonumber(ARGV[4])\n"
            + "local existingTtl = redis.call('PTTL', lockKey)\n"
            + "if existingTtl and existingTtl > 0 then\n"
            + "  return existingTtl\n"
            + "end\n"
            + "local windowStart = now - (windowSec * 1000)\n"
            + "redis.call('ZREMRANGEBYSCORE', zsetKey, '-inf', windowStart - 1)\n"
            + "local seqKey = zsetKey .. ':seq'\n"
            + "local seq = redis.call('INCR', seqKey)\n"
            + "local member = tostring(now) .. '-' .. tostring(seq)\n"
            + "redis.call('ZADD', zsetKey, now, member)\n"
            + "local desiredTtl = (windowSec * 1000) + lockTtl + 5000\n"
            + "local zttl = redis.call('PTTL', zsetKey)\n"
            + "if (not zttl) or (zttl < 0) or (zttl < desiredTtl) then\n"
            + "  redis.call('PEXPIRE', zsetKey, desiredTtl)\n"
            + "  redis.call('PEXPIRE', seqKey, desiredTtl)\n"
            + "end\n"
            + "local cnt = redis.call('ZCOUNT', zsetKey, windowStart, now)\n"
            + "if cnt > limit then\n"
            + "redis.call('SET', lockKey, '1', 'PX', lockTtl)\n"
            + "return lockTtl\n"
            + "end\n"
            + "return 0\n";
    private static final RedisScript<Long> SCRIPT = RedisScript.of(LUA, Long.class);

    private final RedisTemplate<String, String> redis;

    public long checkIpLockAndINCAndMaybeLock(String ip)
    {
        String zsetKey = ZSET_PREFIX + ":" + ip;
        String lockKey = LOCK_PREFIX + ":" + ip;

        long now = System.currentTimeMillis();

        List<String> keys = Arrays.asList(zsetKey, lockKey);
        Object[] args = new Object[]{
                String.valueOf(now),
                String.valueOf(WINDOW_SECONDS),
                String.valueOf(ALLOWED_COUNT),
                String.valueOf(LOCK_TTL_MS)
        };

        Long res = redis.execute(SCRIPT, keys, args);
        return res == null ? 0L : (res + 999)/1000;
    }

}
