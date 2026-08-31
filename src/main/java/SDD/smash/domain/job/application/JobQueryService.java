package SDD.smash.domain.job.application;

import SDD.smash.domain.address.application.AddressQueryService;
import SDD.smash.global.domain.model.SigunguCode;
import SDD.smash.global.exception.DomainException;
import SDD.smash.global.exception.ErrorCode;
import SDD.smash.domain.job.application.dto.JobCategoryView;
import SDD.smash.domain.job.application.dto.JobInfo;
import SDD.smash.domain.job.domain.model.JobCode;
import SDD.smash.domain.job.domain.port.JobCategoryRepository;
import SDD.smash.domain.job.domain.port.JobCountRepository;
import SDD.smash.domain.job.domain.port.JobListingLinkProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 일자리 조회 유스케이스. {@code recommendation} 이 job 을 호출하는 통로다.
 *
 * <p>채용 목록 URL 조립은 {@code JobListingLinkProvider} 포트 뒤로 밀어냈다.
 * 유스케이스는 어느 사이트의 어떤 쿼리 파라미터인지 몰라도 된다.
 */
@Service
@RequiredArgsConstructor
public class JobQueryService {

    private final AddressQueryService addressQueryService;
    private final JobCountRepository jobCountRepository;
    private final JobCategoryRepository jobCategoryRepository;
    private final JobListingLinkProvider jobListingLinkProvider;

    /**
     * 집계 조회라 행이 없어도 0 이 나온다. As-Is 도 이 경로에서 {@code null} 을 돌려준 적이 없다.
     */
    @Transactional(transactionManager = "dataTransactionManager", readOnly = true)
    public JobInfo getJobInfo(SigunguCode sigunguCode) {
        addressQueryService.checkSigunguExistsOrThrow(sigunguCode);

        long total = jobCountRepository.findTotalOf(sigunguCode);
        return new JobInfo(total, jobListingLinkProvider.linkFor(sigunguCode));
    }

    /**
     * 직종 코드가 없으면 검증도 조회도 하지 않고 바로 {@code null} 이다.
     * As-Is 가 시군구 검증보다 먼저 이 분기를 탔으므로 순서를 그대로 유지한다.
     */
    @Transactional(transactionManager = "dataTransactionManager", readOnly = true)
    public JobInfo getJobInfo(SigunguCode sigunguCode, JobCode jobCode) {
        if (jobCode == null) {
            return null;
        }
        addressQueryService.checkSigunguExistsOrThrow(sigunguCode);
        checkSubCategoryExistsOrThrow(jobCode);

        return jobCountRepository.findCountOf(sigunguCode, jobCode)
                .map(count -> new JobInfo(count, jobListingLinkProvider.linkFor(sigunguCode, jobCode)))
                .orElse(null);
    }

    @Transactional(transactionManager = "dataTransactionManager", readOnly = true)
    public List<JobCategoryView> getAllTopCategories() {
        return jobCategoryRepository.findAllTopCategories().stream()
                .map(JobCategoryView::from)
                .toList();
    }

    @Transactional(transactionManager = "dataTransactionManager", readOnly = true)
    public List<JobCategoryView> getSubCategoriesOf(JobCode topCode) {
        if (!jobCategoryRepository.existsTopCategory(topCode)) {
            throw new DomainException(ErrorCode.JOB_CODE_NOT_FOUND, "유효하지 않은 직종코드");
        }
        return jobCategoryRepository.findSubCategoriesOf(topCode).stream()
                .map(JobCategoryView::from)
                .toList();
    }

    /**
     * 중분류가 속한 대분류 코드. 고용행정통계가 대분류 단위라 사용자가 고른 중분류를 여기서 올린다.
     *
     * <p>존재하지 않는 중분류면 {@code JOB_CODE_NOT_FOUND} 다 — 대분류 코드(2자리)가 잘못
     * 넘어와도 중분류 마스터에 없으므로 같은 경로로 걸린다. {@code getJobInfo(sigungu, jobCode)}
     * 가 쓰는 검증과 <b>같은 규칙</b>이라 {@code /api/detail} 과 동작이 갈리지 않는다.
     */
    @Transactional(transactionManager = "dataTransactionManager", readOnly = true)
    public JobCode getTopCodeOfSubOrThrow(JobCode subCode) {
        checkSubCategoryExistsOrThrow(subCode);
        return jobCategoryRepository.findTopCodeOf(subCode)
                .orElseThrow(() -> new DomainException(
                        ErrorCode.JOB_CODE_NOT_FOUND, "직종 중분류의 대분류를 찾을 수 없습니다."));
    }

    private void checkSubCategoryExistsOrThrow(JobCode jobCode) {
        if (!jobCategoryRepository.existsSubCategory(jobCode)) {
            throw new DomainException(ErrorCode.JOB_CODE_NOT_FOUND, "유효하지 않은 직종 코드입니다.");
        }
    }
}
