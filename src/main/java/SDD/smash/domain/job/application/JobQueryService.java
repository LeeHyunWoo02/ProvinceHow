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

    private void checkSubCategoryExistsOrThrow(JobCode jobCode) {
        if (!jobCategoryRepository.existsSubCategory(jobCode)) {
            throw new DomainException(ErrorCode.JOB_CODE_NOT_FOUND, "유효하지 않은 직종 코드입니다.");
        }
    }
}
