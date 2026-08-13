package SDD.smash.domain.job.infrastructure.external;

import SDD.smash.domain.job.domain.model.JobCode;
import SDD.smash.domain.job.domain.model.JobPosting;
import SDD.smash.domain.job.domain.model.JobPostingId;
import SDD.smash.domain.job.infrastructure.external.dto.WorknetApiSpecFile;
import SDD.smash.domain.job.infrastructure.external.dto.WorknetJobPostingRaw;
import SDD.smash.global.domain.model.SigunguCode;
import SDD.smash.global.exception.DomainException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static SDD.smash.global.util.BatchTextUtil.addLeadingZeroThird;
import static SDD.smash.global.util.BatchTextUtil.normalize;

/**
 * 워크넷 코드 체계 → 프로젝트 코드 체계 변환.
 *
 * <h2>규칙</h2>
 * <ul>
 *   <li><b>코드 대 코드로만 옮긴다.</b> 명칭 유사도 매핑은 하지 않는다 — "중구"가 서울에도 부산에도 있다</li>
 *   <li>명시 매핑({@code mapping.regionCodes}/{@code mapping.jobCodes})이 passthrough 보다 우선한다</li>
 *   <li><b>시도 단위 코드({@code NN000})는 특정 시군구로 배분하지 않고 버린다.</b>
 *       시군구 마스터에 {@code 000} 으로 끝나는 코드가 하나도 없어 충돌하지 않는다</li>
 *   <li>매핑되지 않은 코드는 임의 분류하지 않고 <b>버리고 집계</b>한다</li>
 * </ul>
 *
 * <p>시군구/직종이 실제 마스터 테이블에 있는지까지는 보지 않는다. 그 검증은 배치 Processor 가
 * {@code sigungu} / {@code job_code_middle} 대조로 한다(FK 보장).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WorknetCodeMapper {

    /** 시도 대표코드의 꼬리. 시군구 마스터에는 이런 코드가 없다. */
    private static final String SIDO_LEVEL_SUFFIX = "000";

    /** 로그에 남길 미매핑 코드 샘플 상한. 전량을 찍지 않는다. */
    private static final int SAMPLE_LIMIT = 10;

    private final WorknetApiSpecLoader specLoader;

    /**
     * 한 페이지 분량을 옮긴다.
     *
     * @return 옮겨진 공고와 실패 집계
     */
    public PageMapping map(List<WorknetJobPostingRaw> raws) {
        List<JobPosting> postings = new ArrayList<>();
        Set<String> unmappedRegions = new LinkedHashSet<>();
        Set<String> unmappedJobs = new LinkedHashSet<>();
        int unresolvedRegionCount = 0;
        int unresolvedJobCount = 0;

        WorknetApiSpecFile.Mapping mapping = specLoader.spec().mapping();

        for (WorknetJobPostingRaw raw : raws) {
            JobPostingId id = toPostingId(raw.postingId());
            if (id == null) {
                continue;
            }

            Set<SigunguCode> regions = toRegions(raw.regionCodes(), mapping, unmappedRegions);
            Set<JobCode> jobCodes = toJobCodes(raw.jobCodes(), mapping, unmappedJobs);

            if (regions.isEmpty()) {
                unresolvedRegionCount++;
                continue;
            }
            if (jobCodes.isEmpty()) {
                unresolvedJobCount++;
                continue;
            }
            postings.add(new JobPosting(id, regions, jobCodes));
        }

        if (!unmappedRegions.isEmpty()) {
            log.warn("[worknet] 지역코드 매핑 실패 sample={}", sample(unmappedRegions));
        }
        if (!unmappedJobs.isEmpty()) {
            log.warn("[worknet] 직종코드 매핑 실패 sample={}", sample(unmappedJobs));
        }

        return new PageMapping(List.copyOf(postings), unresolvedRegionCount, unresolvedJobCount);
    }

    private JobPostingId toPostingId(String rawId) {
        try {
            return JobPostingId.of(normalize(rawId));
        } catch (DomainException e) {
            log.debug("[worknet] 구인인증번호가 없는 공고를 건너뛴다.");
            return null;
        }
    }

    private Set<SigunguCode> toRegions(List<String> rawCodes,
                                       WorknetApiSpecFile.Mapping mapping,
                                       Set<String> unmapped) {
        Set<SigunguCode> result = new LinkedHashSet<>();
        for (String rawCode : rawCodes) {
            String code = normalize(rawCode);
            if (code == null || code.isEmpty()) {
                continue;
            }
            if (mapping.ignoredRegionCodes().contains(code)) {
                continue;
            }

            String mapped = mapping.regionCodes().get(code);
            if (mapped == null && mapping.regionCodePassthrough()) {
                mapped = code;
            }
            if (mapped == null) {
                unmapped.add(code);
                continue;
            }
            if (mapped.endsWith(SIDO_LEVEL_SUFFIX)) {
                // 시도 단위. 어느 시군구인지 알 수 없으므로 임의 배분하지 않고 버린다.
                continue;
            }
            try {
                result.add(SigunguCode.of(mapped));
            } catch (DomainException e) {
                unmapped.add(code);
            }
        }
        return result;
    }

    private Set<JobCode> toJobCodes(List<String> rawCodes,
                                    WorknetApiSpecFile.Mapping mapping,
                                    Set<String> unmapped) {
        Set<JobCode> result = new LinkedHashSet<>();
        Map<String, String> explicit = mapping.jobCodes();
        for (String rawCode : rawCodes) {
            String code = normalize(rawCode);
            if (code == null || code.isEmpty()) {
                continue;
            }

            String mapped = explicit.get(code);
            if (mapped == null && mapping.jobCodePassthrough()) {
                mapped = addLeadingZeroThird(code);
            }
            if (mapped == null) {
                unmapped.add(code);
                continue;
            }
            try {
                result.add(JobCode.of(addLeadingZeroThird(mapped)));
            } catch (DomainException e) {
                unmapped.add(code);
            }
        }
        return result;
    }

    private List<String> sample(Set<String> codes) {
        return codes.stream().limit(SAMPLE_LIMIT).toList();
    }

    /**
     * 한 페이지 변환 결과.
     *
     * @param postings              집계 가능한 공고
     * @param unresolvedRegionCount 지역을 못 옮겨 버린 공고 수
     * @param unresolvedJobCount    직종을 못 옮겨 버린 공고 수
     */
    public record PageMapping(List<JobPosting> postings,
                              int unresolvedRegionCount,
                              int unresolvedJobCount) {
    }
}
