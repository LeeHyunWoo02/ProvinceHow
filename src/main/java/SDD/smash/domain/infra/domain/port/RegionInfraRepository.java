package SDD.smash.domain.infra.domain.port;

import SDD.smash.global.domain.model.SigunguCode;
import SDD.smash.domain.infra.domain.model.RegionInfra;

/**
 * 시군구별 인프라 상세 저장소 out-port.
 *
 * <p>대상 시군구에 적재된 인프라가 없으면 빈 목록을 가진 {@link RegionInfra} 를 돌려준다
 * ("코드 없음"이 아니라 "데이터 없음"이므로 {@code Optional} 이 아니다).
 */
public interface RegionInfraRepository {

    RegionInfra findBy(SigunguCode sigunguCode);
}
