package SDD.smash.domain.support.domain.port;

import SDD.smash.global.domain.model.SigunguCode;
import SDD.smash.domain.support.domain.model.SupportPolicy;
import SDD.smash.domain.support.domain.model.SupportTag;

import java.util.List;

/**
 * 외부 청년정책 공급 out-port. As-Is {@code YouthCenterClient} 자리다.
 *
 * <p>어느 기관의 어떤 프로토콜인지는 어댑터가 안다. 도메인은
 * "시군구와 태그로 지원정책 목록을 받는다"만 안다. 조회 실패 시 빈 목록을 돌려준다
 * (어댑터가 흡수한다 — dwelling 의 {@code RentRecordProvider} 와 달리 여기는 재시도할
 * 배치가 없어 예외를 다시 던져도 얻을 이득이 없다. As-Is 도 실패 시 빈 응답으로 처리했다).
 */
public interface SupportPolicyProvider {

    List<SupportPolicy> fetch(SigunguCode code, SupportTag tag);
}
