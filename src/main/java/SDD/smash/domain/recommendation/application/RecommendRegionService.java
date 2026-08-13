package SDD.smash.domain.recommendation.application;

import SDD.smash.domain.address.application.AddressQueryService;
import SDD.smash.domain.address.application.dto.RegionCodeView;
import SDD.smash.global.domain.model.Score;
import SDD.smash.global.domain.model.SigunguCode;
import SDD.smash.domain.dwelling.application.DwellingQueryService;
import SDD.smash.domain.dwelling.application.DwellingScoreService;
import SDD.smash.domain.infra.application.InfraQueryService;
import SDD.smash.domain.infra.application.InfraScoreService;
import SDD.smash.domain.job.application.JobQueryService;
import SDD.smash.domain.job.application.JobScoreService;
import SDD.smash.domain.recommendation.application.dto.DwellingSimpleInfoSummary;
import SDD.smash.domain.recommendation.application.dto.JobInfoSummary;
import SDD.smash.domain.recommendation.application.dto.MajorInfraSummaryItem;
import SDD.smash.domain.recommendation.application.dto.RecommendCommand;
import SDD.smash.domain.recommendation.application.dto.RegionRecommendation;
import SDD.smash.domain.recommendation.domain.model.RegionScore;
import SDD.smash.domain.recommendation.domain.service.RegionScorePolicy;
import SDD.smash.domain.support.application.SupportQueryService;
import SDD.smash.domain.support.application.SupportScoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toMap;

/**
 * 지역 추천 유스케이스.
 *
 * <p>job/dwelling/support/infra 네 컨텍스트의 점수·조회 application Service 만 호출한다.
 * 다른 컨텍스트의 domain 모델이나 Repository 는 쓰지 않는다.
 *
 * <p>조합 규칙(비대칭 나눗셈, 서울·경기·인천 제외, 상위 10개 재정규화)은
 * {@code RegionScorePolicy}(순수 도메인 정책)에 있다 — 여기는 오케스트레이션만 한다.
 */
@Service
@RequiredArgsConstructor
public class RecommendRegionService {

    private final JobScoreService jobScoreService;
    private final DwellingScoreService dwellingScoreService;
    private final SupportScoreService supportScoreService;
    private final InfraScoreService infraScoreService;

    private final JobQueryService jobQueryService;
    private final DwellingQueryService dwellingQueryService;
    private final SupportQueryService supportQueryService;
    private final InfraQueryService infraQueryService;

    private final AddressQueryService addressQueryService;

    private final RegionScorePolicy policy = new RegionScorePolicy();

    public List<RegionRecommendation> recommend(RecommendCommand command) {

        // 1) 네 컨텍스트의 점수 맵을 가져온다.
        Map<SigunguCode, Score> jobScores = jobScoreService.scoresFor(command.jobCode());
        Map<SigunguCode, Score> dwellingScores = dwellingScoreService.scoresFor(command.dwellingType(), command.budget());
        Map<SigunguCode, Score> supportScores = supportScoreService.scoresFor(command.supportChoice());
        Map<SigunguCode, Score> infraScores = infraScoreService.scoresFor(command.infraChoice());

        boolean supportSelected = isSelected(command.supportChoice());
        boolean infraSelected = isSelected(command.infraChoice());

        // 2) 전 시군구를 돌며 제외 대상(서울/경기/인천)을 걸러내고 조합 점수를 만든다.
        List<RegionCodeView> allRegions = addressQueryService.getAllRegionCodes();

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

                    .totalJobInfo(JobInfoSummary.from(jobQueryService.getJobInfo(code)))
                    .fitJobInfo(JobInfoSummary.from(jobQueryService.getJobInfo(code, command.jobCode())))

                    .totalSupportNum(supportQueryService.getAllSupportCount(code))
                    .fitSupportNum(supportQueryService.getFitSupportCount(code, command.supportChoice()))

                    .dwellingSimpleInfo(DwellingSimpleInfoSummary.from(dwellingQueryService.getDwellingSimpleInfo(code)))

                    .infraMajors(infraQueryService.getMajorInfraSummaries(code).stream()
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
