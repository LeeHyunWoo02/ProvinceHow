package SDD.smash.domain.job.domain.model;

import SDD.smash.global.domain.model.SigunguCode;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 외부에서 수집한 채용공고 한 건. {@code JobCount} 집계의 원재료다.
 *
 * <p><b>이 모델에는 외부 공급자의 어휘가 없다.</b> 공급자의 지역코드/직종코드는
 * 어댑터에서 이미 {@link SigunguCode}/{@link JobCode} 로 번역됐고, 번역되지 않은 코드는
 * 어댑터가 버린다. 따라서 여기 남은 코드는 전부 우리 코드 체계다.
 *
 * <p>한 공고가 여러 시군구·여러 직종에 걸릴 수 있다(다지역 채용, 복수 직종 모집).
 * 집계 규칙은 {@link #countKeys()} 가 정의한다.
 *
 * @param id       공고 식별자 (중복 제거 기준)
 * @param regions  이 공고가 실제로 지원 가능한 시군구들. 비어 있으면 집계 대상이 아니다
 * @param jobCodes 이 공고의 직종(중분류)들. 비어 있으면 집계 대상이 아니다
 */
public record JobPosting(JobPostingId id, Set<SigunguCode> regions, Set<JobCode> jobCodes) {

    public JobPosting {
        regions = (regions == null) ? Set.of() : Set.copyOf(regions);
        jobCodes = (jobCodes == null) ? Set.of() : Set.copyOf(jobCodes);
    }

    /** 시군구와 직종이 모두 확정된 공고만 집계에 들어간다. */
    public boolean isCountable() {
        return !regions.isEmpty() && !jobCodes.isEmpty();
    }

    /**
     * 이 공고가 1건씩 기여할 (시군구, 직종) 조합.
     *
     * <p><b>정책: 안분(按分)하지 않고 각 조합에 1건씩 센다.</b>
     * {@code JobCount} 는 "그 시군구에서 그 직종으로 지원할 수 있는 공고 수"이고,
     * 다지역 공고는 각 지역에서 실제로 지원 가능하므로 각 지역에서 1건이 맞다.
     * 1/N 로 나누면 정수가 아니고, 어느 지역에 몰아주는 것은 임의 배분이다.
     * 대가로 <b>전 지역 합계는 전국 공고 수보다 크다</b>(docs/worknet-job-api.md).
     */
    public List<JobCountKey> countKeys() {
        Set<JobCountKey> keys = new LinkedHashSet<>();
        for (SigunguCode region : regions) {
            for (JobCode jobCode : jobCodes) {
                keys.add(new JobCountKey(region, jobCode));
            }
        }
        return List.copyOf(keys);
    }
}
