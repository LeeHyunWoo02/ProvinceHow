package SDD.smash.recommendation.application;

import SDD.smash.address.application.dto.RegionCodeView;
import SDD.smash.address.application.port.in.AddressQueryUseCase;
import SDD.smash.common.domain.model.Score;
import SDD.smash.common.domain.model.SigunguCode;
import SDD.smash.dwelling.application.port.in.DwellingQueryUseCase;
import SDD.smash.dwelling.application.port.in.DwellingScoreUseCase;
import SDD.smash.infra.application.port.in.InfraQueryUseCase;
import SDD.smash.infra.application.port.in.InfraScoreUseCase;
import SDD.smash.job.application.port.in.JobQueryUseCase;
import SDD.smash.job.application.port.in.JobScoreUseCase;
import SDD.smash.recommendation.application.dto.DwellingSimpleInfoSummary;
import SDD.smash.recommendation.application.dto.JobInfoSummary;
import SDD.smash.recommendation.application.dto.MajorInfraSummaryItem;
import SDD.smash.recommendation.application.dto.RecommendCommand;
import SDD.smash.recommendation.application.dto.RegionRecommendation;
import SDD.smash.recommendation.application.port.in.RecommendRegionUseCase;
import SDD.smash.recommendation.domain.model.RegionScore;
import SDD.smash.recommendation.domain.service.RegionScorePolicy;
import SDD.smash.support.application.port.in.SupportQueryUseCase;
import SDD.smash.support.application.port.in.SupportScoreUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toMap;

/**
 * 지역 추천 유스케이스. As-Is {@code RecommendService.recommend} 를 옮긴 것이다.
 *
 * <p>job/dwelling/support/infra 네 컨텍스트의 점수 in-port·조회 in-port만 호출한다.
 * 조합 규칙(비대칭 나눗셈, 서울·경기·인천 제외, 상위 10개 재정규화)은
 * {@code RegionScorePolicy}(순수 도메인 정책)에 있다 — 여기는 오케스트레이션만 한다.
 */
@Service
@RequiredArgsConstructor
public class RecommendRegionService implements RecommendRegionUseCase {

    private final JobScoreUseCase jobScoreUseCase;
    private final DwellingScoreUseCase dwellingScoreUseCase;
    private final SupportScoreUseCase supportScoreUseCase;
    private final InfraScoreUseCase infraScoreUseCase;

    private final JobQueryUseCase jobQueryUseCase;
    private final DwellingQueryUseCase dwellingQueryUseCase;
    private final SupportQueryUseCase supportQueryUseCase;
    private final InfraQueryUseCase infraQueryUseCase;

    private final AddressQueryUseCase addressQueryUseCase;

    private final RegionScorePolicy policy = new RegionScorePolicy();

    @Override
    public List<RegionRecommendation> recommend(RecommendCommand command) {

        // 1) 네 컨텍스트의 점수 맵을 가져온다.
        Map<SigunguCode, Score> jobScores = jobScoreUseCase.scoresFor(command.jobCode());
        Map<SigunguCode, Score> dwellingScores = dwellingScoreUseCase.scoresFor(command.dwellingType(), command.budget());
        Map<SigunguCode, Score> supportScores = supportScoreUseCase.scoresFor(command.supportChoice());
        Map<SigunguCode, Score> infraScores = infraScoreUseCase.scoresFor(command.infraChoice());

        boolean supportSelected = isSelected(command.supportChoice());
        boolean infraSelected = isSelected(command.infraChoice());

        // 2) 전 시군구를 돌며 제외 대상(서울/경기/인천)을 걸러내고 조합 점수를 만든다.
        List<RegionCodeView> allRegions = addressQueryUseCase.getAllRegionCodes();

        List<RegionScore> candidates = new ArrayList<>();
        for (RegionCodeView region : allRegions) {
            if (policy.isExcluded(region.sidoCode())) {
                continue;
            }
            SigunguCode code = region.sigunguCode();
            Score jobScore = jobScores.getOrDefault(code, Score.ZERO);
            Score dwellingScore = dwellingScores.getOrDefault(code, Score.ZERO);
            Score supportScore = supportScores.getOrDefault(code, Score.ZERO);
            Score infraScore = infraScores.getOrDefault(code, Score.ZERO);

            Score combined = policy.combine(jobScore, dwellingScore, supportScore, infraScore,
                    supportSelected, infraSelected);
            candidates.add(new RegionScore(code, region.sidoCode(), combined));
        }

        // 3) 상위 10개를 고르고 재정규화한다.
        List<RegionScore> top10 = policy.selectTopTenRenormalized(candidates);

        // 4) 상위 10개 각각의 상세 정보를 채운다.
        Map<SigunguCode, RegionCodeView> regionByCode = allRegions.stream()
                .collect(toMap(RegionCodeView::sigunguCode, r -> r));

        List<RegionRecommendation> result = new ArrayList<>();
        for (RegionScore regionScore : top10) {
            SigunguCode code = regionScore.sigunguCode();
            RegionCodeView region = regionByCode.get(code);

            result.add(RegionRecommendation.builder()
                    .sidoCode(region.sidoCode().value())
                    .sidoName(region.sidoName())
                    .sigunguCode(code.value())
                    .sigunguName(region.sigunguName())
                    .score(regionScore.score().value())

                    .totalJobInfo(JobInfoSummary.from(jobQueryUseCase.getJobInfo(code)))
                    .fitJobInfo(JobInfoSummary.from(jobQueryUseCase.getJobInfo(code, command.jobCode())))

                    .totalSupportNum(supportQueryUseCase.getAllSupportCount(code))
                    .fitSupportNum(supportQueryUseCase.getFitSupportCount(code, command.supportChoice()))

                    .dwellingSimpleInfo(DwellingSimpleInfoSummary.from(dwellingQueryUseCase.getDwellingSimpleInfo(code)))

                    .infraMajors(infraQueryUseCase.getMajorInfraSummaries(code).stream()
                            .map(MajorInfraSummaryItem::from)
                            .toList())
                    .build());
        }
        return result;
    }

    private boolean isSelected(Integer choice) {
        return choice != null && choice != 0;
    }
}
