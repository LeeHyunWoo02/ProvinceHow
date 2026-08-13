package SDD.smash.domain.infra.infrastructure.master;

import SDD.smash.domain.infra.domain.model.IndustryCode;
import SDD.smash.domain.infra.domain.model.Major;

/**
 * 업종 마스터 한 줄. 설정 파일({@code infra/industry-master.yml})의 정본을 그대로 담는다.
 *
 * <p>외부 서비스 식별자(slug, 데이터셋 ID, 구 {@code opnSvcId})는 <b>어댑터 어휘</b>라
 * 이 클래스와 {@code infrastructure} 안에만 존재한다. 도메인은 {@link IndustryCode} 만 안다.
 *
 * @param code            내부 업종 코드. {@code industry.industry_code}(varchar 10)에 그대로 들어간다
 * @param name            표시 이름
 * @param major           인프라 대분류. <b>{@code null} 이면 미확정</b>이며 이 경우 활성화될 수 없다
 * @param slug            신 data.go.kr 엔드포인트 slug ({@code apis.data.go.kr/1741000/{slug}/info})
 * @param datasetId       data.go.kr 데이터셋 ID. 활용신청 화면을 찾는 데 쓴다(호출에는 쓰지 않는다)
 * @param enabled         수집·적재 대상인가
 * @param majorReviewed   {@code major} 배정을 사람이 확인했는가. {@code false} 면 제안 상태다
 * @param note            운영 메모. 로그·문서용이며 동작에 영향을 주지 않는다
 */
public record IndustryMasterEntry(IndustryCode code, String name, Major major, String slug,
                                  String datasetId, boolean enabled, boolean majorReviewed, String note) {

    /** 실제로 수집·적재할 수 있는 항목인가. 대분류가 없으면 {@code industry} 행을 만들 수 없다. */
    public boolean isActive() {
        return enabled && major != null && slug != null && !slug.isBlank();
    }
}
