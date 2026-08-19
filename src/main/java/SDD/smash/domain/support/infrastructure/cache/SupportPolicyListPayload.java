package SDD.smash.domain.support.infrastructure.cache;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 지원정책 목록 캐시 페이로드. redis-conventions §3.1 이 요구하는 전용
 * {@code RedisTemplate<String, SupportPolicyListPayload>} 의 값 타입이다.
 * {@code global/config/RedisConfig} 가 이 클래스를 참조해 전용 템플릿 빈을 만든다
 * (As-Is 의 {@code supportListDTORedisTemplate} 이 옛 {@code SupportListDTO} 를 참조하던
 * 것과 같은 방식 — 이 프로젝트에서는 컨텍스트 기술 DTO를 공통 설정이 참조하는 것이 이미 관례다).
 *
 * <p>{@code collectedAt} 은 이 정책 목록을 <b>외부에서 수집해 저장한 시각</b>(ISO-8601, UTC)이다.
 * 정본 저장소에서 TTL 을 없앴기 때문에 "언제 받은 데이터인가"를 만료 여부로 알 수 없게 됐고,
 * 그 판단 근거를 데이터와 같은 키에 함께 남긴다. 필드를 맨 앞에 둬 {@code redis-cli GET} 의
 * 앞부분만 봐도 수집 시각이 보이게 했다.
 *
 * <p>{@code Instant} 가 아니라 문자열인 이유 — 이 값의 직렬화기({@code RedisConfig} 의 전용
 * {@code ObjectMapper})에 JavaTimeModule 이 등록돼 있지 않다. 문자열이 사람이 읽기도 쉽다.
 * TTL 도입 이전에 저장된 옛 페이로드에는 이 필드가 없어 {@code null} 로 복원된다(= 수집 시각 미상).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SupportPolicyListPayload {

    private String collectedAt;

    private List<SupportPolicyPayload> policies;
}
