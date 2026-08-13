package SDD.smash.domain.infra.domain.port;

import SDD.smash.domain.infra.domain.model.FacilityCollection;
import SDD.smash.domain.infra.domain.model.IndustryCode;
import SDD.smash.domain.infra.domain.model.LocalDataRegionCode;

/**
 * 인허가 사업장 공급 out-port.
 *
 * <p>구현은 두 가지다 — 공식 API 어댑터와 벌크 CSV 어댑터. 어느 쪽인지는
 * {@code infra.collect.source} 프로퍼티가 정하고, 도메인·배치는 구분하지 않는다.
 *
 * <p>수집 실패는 <b>예외로 알린다.</b> 빈 목록으로 삼키면 "그 지역에 시설이 0개"로 집계돼
 * 잘못된 스냅샷이 만들어진다. 부분 실패를 감지해야 기존 스냅샷을 보존할 수 있다.
 */
public interface InfraFacilityProvider {

    /**
     * 한 (업종, 인허가기관) 조합의 사업장을 전부 수집한다.
     * 페이지네이션과 중복 제거는 구현이 책임진다.
     *
     * @throws RuntimeException 수집을 완료하지 못했을 때
     */
    FacilityCollection collect(IndustryCode industryCode, LocalDataRegionCode regionCode);

    /** 이 공급자가 쓸 준비가 됐는가(예: 인증키 보유). 준비되지 않았으면 배치를 건너뛴다. */
    boolean isReady();

    /** 준비되지 않은 이유. 로그에 남길 수 있게 <b>비밀값을 포함하지 않는</b> 문구여야 한다. */
    String readinessDescription();
}
