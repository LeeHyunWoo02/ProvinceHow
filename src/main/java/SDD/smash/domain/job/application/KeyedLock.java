package SDD.smash.domain.job.application;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 키 단위 in-process 락. 콜드 캐시 스탬피드(동시 요청이 각자 외부 API 를 때리는 문제)를 완화하는
 * 단일비행(single-flight) 도구다.
 *
 * <p><b>단일 인스턴스 전제.</b> 현재 배포는 backend 컨테이너 1개(docker-compose)라 in-process 락으로
 * 충분하다 — 분산 락(Redis)은 없는 요구를 발명하는 과설계라 도입하지 않는다. 인스턴스를 늘리면
 * 인스턴스별로 최대 1회씩 외부 호출이 날 수 있으나, 그때 Redis 락을 검토한다.
 *
 * <p>키 공간이 유한하다(시군구 264개). 락 객체를 제거하지 않아도 누수가 없어 정리 로직을 두지 않는다.
 */
class KeyedLock<K> {

    private final ConcurrentHashMap<K, ReentrantLock> locks = new ConcurrentHashMap<>();

    ReentrantLock forKey(K key) {
        return locks.computeIfAbsent(key, k -> new ReentrantLock());
    }
}
