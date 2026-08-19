package SDD.smash.domain.infra.infrastructure.batch.dto;

import SDD.smash.domain.infra.domain.model.IndustryCode;
import SDD.smash.domain.infra.domain.model.LocalDataRegionCode;

/**
 * 수집 대상 하나 — (인허가기관, 업종) 조합이다. 체크포인트의 최소 단위이기도 하다.
 *
 * <p>이 단위로 "수집 완료"를 기록하기 때문에, 대상 처리가 중간에 끊기면 그 대상은
 * <b>통째로</b> 미완료로 남는다. 부분 페이지만 저장하는 경로는 없다.
 */
public record InfraCollectTarget(IndustryCode industryCode, LocalDataRegionCode regionCode) {

    public String industryCodeValue() {
        return industryCode.value();
    }

    public String regionCodeValue() {
        return regionCode.value();
    }
}
