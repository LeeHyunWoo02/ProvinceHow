package SDD.smash.infra.domain.port;

import SDD.smash.infra.domain.model.Major;
import SDD.smash.infra.domain.model.RegionMajorScore;

import java.util.List;
import java.util.Set;

/** 전 시군구의 대분류별 평균 점수 저장소 out-port. 점수 계산의 원천 데이터를 공급한다. */
public interface RegionMajorScoreRepository {

    List<RegionMajorScore> findAllBy(Set<Major> majors);
}
