package SDD.smash.domain.support.domain.port;

import SDD.smash.global.domain.model.SigunguCode;
import SDD.smash.domain.support.domain.model.SupportPolicyCollection;
import SDD.smash.domain.support.domain.model.SupportTag;

/**
 * 외부 청년정책 공급 out-port.
 *
 * <p>어느 기관의 어떤 프로토콜인지는 어댑터가 안다. 도메인은 "시군구와 태그로 지원정책을
 * 수집한다"만 안다.
 *
 * <p><b>실패를 빈 목록으로 돌려주지 않는다.</b> 예전에는 실패를 어댑터가 빈 목록으로 흡수했는데,
 * 그 빈 목록이 정본 저장소에 그대로 저장되어 일시적인 500 한 번이 멀쩡한 정책을 지웠다.
 * 지금은 {@link SupportPolicyCollection} 이 "수집했는가"를 함께 들고 오고, 수집하지 못한
 * 조합은 호출자가 저장을 건너뛰어 기존 데이터를 보존한다. 재시도·호출 간격은 어댑터 안에서
 * 끝내므로 포트에는 HTTP 어휘가 없다.
 */
public interface SupportPolicyProvider {

    SupportPolicyCollection fetch(SigunguCode code, SupportTag tag);
}
