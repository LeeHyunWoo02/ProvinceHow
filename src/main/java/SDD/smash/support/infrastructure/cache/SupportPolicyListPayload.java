package SDD.smash.support.infrastructure.cache;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 지원정책 목록 캐시 페이로드. redis-conventions §3.1 이 요구하는 전용
 * {@code RedisTemplate<String, SupportPolicyListPayload>} 의 값 타입이다.
 * {@code common/config/RedisConfig} 가 이 클래스를 참조해 전용 템플릿 빈을 만든다
 * (As-Is 의 {@code supportListDTORedisTemplate} 이 옛 {@code SupportListDTO} 를 참조하던
 * 것과 같은 방식 — 이 프로젝트에서는 컨텍스트 기술 DTO를 공통 설정이 참조하는 것이 이미 관례다).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SupportPolicyListPayload {

    private List<SupportPolicyPayload> policies;
}
