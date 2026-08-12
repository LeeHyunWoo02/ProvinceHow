package SDD.smash.domain.recommendation.domain.service;

import SDD.smash.global.domain.model.Score;
import SDD.smash.global.domain.model.SidoCode;
import SDD.smash.domain.recommendation.domain.model.RegionScore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * job/dwelling/infra/support 네 컨텍스트의 점수를 합쳐 지역 추천 순위를 만든다.
 * "여러 컨텍스트의 점수를 조합하는 규칙"이라 recommendation 도메인에 둔다
 * (backend-conventions §4.1 — 여러 Aggregate/컨텍스트의 데이터가 필요한 규칙).
 *
 * <p>As-Is {@code RecommendService.recommend} 의 조합·정렬·재정규화 부분을 그대로 옮겼다.
 * 저장소·캐시에 의존하지 않는 순수 함수다 — 네 점수 맵을 채우는 것은 application의 몫이다.
 */
public class RegionScorePolicy {

    /** 서울(11)·경기(41)·인천(28)은 추천 대상에서 제외한다. As-Is 그대로다. */
    private static final Set<SidoCode> EXCLUDED_SIDO_CODES = Set.of(
            SidoCode.of("11"), SidoCode.of("41"), SidoCode.of("28"));

    private static final int MAX_RESULTS = 10;
    private static final int RENORMALIZED_MAX = 100;

    public boolean isExcluded(SidoCode sidoCode) {
        return EXCLUDED_SIDO_CODES.contains(sidoCode);
    }

    /**
     * 네 점수를 더해 나눈다.
     *
     * <p><b>나눗셈이 비대칭이다</b> — job과 dwelling은 사용자가 무엇을 고르든 항상 나눗셈에
     * 들어가지만, support/infra는 선택하지 않았을 때(널 또는 0) 분모에서 빠진다.
     * 정수 나눗셈이라 소수점이 버려지는 것까지 As-Is 그대로다.
     */
    public Score combine(Score jobScore, Score dwellingScore, Score supportScore, Score infraScore,
                         boolean supportSelected, boolean infraSelected) {
        int div = 4;
        if (!supportSelected) {
            div--;
        }
        if (!infraSelected) {
            div--;
        }
        int sum = jobScore.value() + dwellingScore.value() + supportScore.value() + infraScore.value();
        return Score.of(sum / div);
    }

    /**
     * 점수 내림차순으로 상위 10개를 고르고, 1위 점수를 100으로 재정규화한다.
     *
     * <p>1위 점수가 0이면(전부 0점) {@code (double) 0 / 0} 이 {@code NaN} 이 되고
     * {@code Math.round(NaN)} 은 0을 돌려준다 — As-Is가 이 경우를 별도로 방어하지
     * 않았으므로 여기서도 그 결과(전원 0점)를 그대로 재현한다.
     */
    public List<RegionScore> selectTopTenRenormalized(List<RegionScore> candidates) {
        List<RegionScore> sorted = new ArrayList<>(candidates);
        sorted.sort(Comparator.comparing((RegionScore r) -> r.score().value()).reversed());

        List<RegionScore> top = sorted.size() > MAX_RESULTS ? sorted.subList(0, MAX_RESULTS) : sorted;

        // As-Is 도 빈 목록을 방어하지 않았다 — 항상 후보가 있는 실제 운영 데이터를 전제한다.
        int maxScore = top.get(0).score().value();

        List<RegionScore> renormalized = new ArrayList<>();
        for (RegionScore candidate : top) {
            int scaled = (int) Math.round(((double) candidate.score().value() / maxScore) * RENORMALIZED_MAX);
            renormalized.add(new RegionScore(candidate.sigunguCode(), candidate.sidoCode(), Score.of(scaled)));
        }
        return renormalized;
    }
}
