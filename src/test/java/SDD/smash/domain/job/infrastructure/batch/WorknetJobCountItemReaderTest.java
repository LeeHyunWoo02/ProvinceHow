package SDD.smash.domain.job.infrastructure.batch;

import SDD.smash.domain.job.domain.model.JobCode;
import SDD.smash.domain.job.domain.model.JobPosting;
import SDD.smash.domain.job.domain.model.JobPostingId;
import SDD.smash.domain.job.domain.model.JobPostingPage;
import SDD.smash.domain.job.domain.port.JobPostingProvider;
import SDD.smash.domain.job.infrastructure.batch.dto.JobCountCsvRow;
import SDD.smash.domain.job.infrastructure.persistence.JobCountJpaRepository;
import SDD.smash.domain.job.infrastructure.persistence.projection.JobCountKeyRow;
import SDD.smash.global.domain.model.SigunguCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.item.ExecutionContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class WorknetJobCountItemReaderTest {

    @Mock
    JobCountJpaRepository jobCountJpaRepository;

    @Test
    @DisplayName("여러 페이지를 끝까지 당겨와 (시군구, 직종)별로 집계한다")
    void aggregatesAcrossMultiplePages() throws Exception {
        // given - 전체 3건이 2건 + 1건 두 페이지로 내려온다
        StubProvider provider = new StubProvider(List.of(
                page(1, 3, posting("A", "11110", "011"), posting("B", "11110", "011")),
                page(2, 3, posting("C", "11140", "012"))));

        // when
        List<JobCountCsvRow> rows = readAll(reader(provider, 2, true, false));

        // then
        assertThat(provider.calls()).isEqualTo(2);
        assertThat(rows).extracting(JobCountCsvRow::sigunguCode, JobCountCsvRow::middleCode, JobCountCsvRow::count)
                .containsExactlyInAnyOrder(
                        tuple("11110", "011", 2),
                        tuple("11140", "012", 1));
    }

    @Test
    @DisplayName("빈 페이지를 만나면 더 요청하지 않는다")
    void stopsOnEmptyPage() throws Exception {
        // given - 전체 건수를 모른다고 응답해도 빈 페이지에서 멈춰야 한다
        StubProvider provider = new StubProvider(List.of(
                new JobPostingPage(1, JobPostingPage.UNKNOWN_TOTAL, List.of(posting("A", "11110", "011")), 0, 0),
                JobPostingPage.empty(2)));

        // when
        readAll(reader(provider, 100, true, false));

        // then
        assertThat(provider.calls()).isEqualTo(2);
    }

    @Test
    @DisplayName("페이지 경계에서 같은 공고가 두 번 와도 한 번만 센다")
    void deduplicatesPostingRepeatedAcrossPages() throws Exception {
        // given
        StubProvider provider = new StubProvider(List.of(
                page(1, 4, posting("A", "11110", "011"), posting("B", "11110", "011")),
                page(2, 4, posting("B", "11110", "011"), posting("C", "11110", "011"))));

        // when
        List<JobCountCsvRow> rows = readAll(reader(provider, 2, true, false));

        // then
        assertThat(rows).singleElement()
                .extracting(JobCountCsvRow::count).isEqualTo(3);
    }

    @Test
    @DisplayName("중복 제거를 끄면 중복 공고가 그대로 집계된다")
    void keepsDuplicatesWhenDedupeDisabled() throws Exception {
        // given
        StubProvider provider = new StubProvider(List.of(
                page(1, 4, posting("A", "11110", "011"), posting("B", "11110", "011")),
                page(2, 4, posting("B", "11110", "011"), posting("C", "11110", "011"))));

        // when
        List<JobCountCsvRow> rows = readAll(reader(provider, 2, false, false));

        // then
        assertThat(rows).singleElement()
                .extracting(JobCountCsvRow::count).isEqualTo(4);
    }

    @Test
    @DisplayName("다지역·다직종 공고는 모든 조합에 1건씩 들어간다")
    void spreadsMultiRegionMultiJobPostingAcrossEveryCombination() throws Exception {
        // given
        JobPosting posting = new JobPosting(
                JobPostingId.of("A"),
                Set.of(SigunguCode.of("11110"), SigunguCode.of("11140")),
                Set.of(JobCode.of("011"), JobCode.of("012")));
        StubProvider provider = new StubProvider(List.of(page(1, 1, posting)));

        // when
        List<JobCountCsvRow> rows = readAll(reader(provider, 100, true, false));

        // then
        assertThat(rows).hasSize(4).allMatch(row -> row.count() == 1);
    }

    @Test
    @DisplayName("이번 스냅샷에서 사라진 조합은 0으로 내린다")
    void resetsDisappearedCombinationsToZero() throws Exception {
        // given - 지난 회차에는 11140/012 가 있었는데 이번엔 안 왔다
        given(jobCountJpaRepository.findAllKeys()).willReturn(List.of(
                new JobCountKeyRow("11110", "011"),
                new JobCountKeyRow("11140", "012")));
        StubProvider provider = new StubProvider(List.of(page(1, 1, posting("A", "11110", "011"))));

        // when
        List<JobCountCsvRow> rows = readAll(reader(provider, 100, true, true));

        // then
        assertThat(rows).extracting(JobCountCsvRow::sigunguCode, JobCountCsvRow::middleCode, JobCountCsvRow::count)
                .containsExactlyInAnyOrder(
                        tuple("11110", "011", 1),
                        tuple("11140", "012", 0));
    }

    @Test
    @DisplayName("0 리셋을 끄면 사라진 조합을 건드리지 않는다")
    void leavesDisappearedCombinationsAloneWhenResetDisabled() throws Exception {
        // given
        StubProvider provider = new StubProvider(List.of(page(1, 1, posting("A", "11110", "011"))));

        // when
        List<JobCountCsvRow> rows = readAll(reader(provider, 100, true, false));

        // then
        assertThat(rows).hasSize(1);
        then(jobCountJpaRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("같은 기준일로 다시 읽어도 같은 결과가 나온다")
    void producesSameRowsOnRerunForSameBaseDate() throws Exception {
        // given
        given(jobCountJpaRepository.findAllKeys()).willReturn(List.of(new JobCountKeyRow("11110", "011")));

        // when - 같은 응답을 주는 공급자로 두 번 읽는다
        List<JobCountCsvRow> first = readAll(
                reader(new StubProvider(List.of(page(1, 2, posting("A", "11110", "011"),
                        posting("B", "11110", "011")))), 100, true, true));
        List<JobCountCsvRow> second = readAll(
                reader(new StubProvider(List.of(page(1, 2, posting("A", "11110", "011"),
                        posting("B", "11110", "011")))), 100, true, true));

        // then
        assertThat(second).isEqualTo(first);
    }

    @Test
    @DisplayName("페이지 상한에 걸리면 더 요청하지 않는다")
    void stopsAtConfiguredPageLimit() throws Exception {
        // given - 전체 건수를 모른다고 계속 응답하는 공급자
        StubProvider provider = new StubProvider(List.of(
                new JobPostingPage(1, JobPostingPage.UNKNOWN_TOTAL, List.of(posting("A", "11110", "011")), 0, 0),
                new JobPostingPage(2, JobPostingPage.UNKNOWN_TOTAL, List.of(posting("B", "11110", "011")), 0, 0),
                new JobPostingPage(3, JobPostingPage.UNKNOWN_TOTAL, List.of(posting("C", "11110", "011")), 0, 0)));
        WorknetJobCountItemReader reader = new WorknetJobCountItemReader(
                provider, jobCountJpaRepository, 100, 2, 100_000, true, false);

        // when
        readAll(reader);

        // then
        assertThat(provider.calls()).isEqualTo(2);
    }

    @Test
    @DisplayName("호출 예산 상한 전에 수집이 끝나지 않으면 이번 run 결과를 반영하지 않는다(기존 데이터 보존)")
    void abandonsRunWhenCallBudgetExhaustedBeforeCompletion() throws Exception {
        // given - 계속 다음 페이지가 있다고 응답해 전수 수집이 예산 안에 끝나지 않는 상황
        StubProvider provider = new StubProvider(List.of(
                new JobPostingPage(1, JobPostingPage.UNKNOWN_TOTAL, List.of(posting("A", "11110", "011")), 0, 0),
                new JobPostingPage(2, JobPostingPage.UNKNOWN_TOTAL, List.of(posting("B", "11110", "011")), 0, 0),
                new JobPostingPage(3, JobPostingPage.UNKNOWN_TOTAL, List.of(posting("C", "11110", "011")), 0, 0)));
        // maxPages 는 넉넉하지만 maxApiCalls=2 가 실제 상한. 0 리셋을 켜도 부분 수집이면 건드리면 안 된다.
        WorknetJobCountItemReader reader = new WorknetJobCountItemReader(
                provider, jobCountJpaRepository, 100, 1000, 2, true, true);

        // when
        List<JobCountCsvRow> rows = readAll(reader);

        // then - 예산만큼만(2회) 부르고, 부분 스냅샷이라 아무 행도 내보내지 않으며 0 리셋도 하지 않는다
        assertThat(provider.calls()).isEqualTo(2);
        assertThat(rows).isEmpty();
        then(jobCountJpaRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("예산 안에서 수집이 끝나면 정상 집계하고 0 리셋도 적용한다")
    void appliesResultsWhenCollectionCompletesWithinBudget() throws Exception {
        // given - 지난 회차의 11140/012 가 이번엔 안 왔고, 수집은 1페이지로 끝난다(예산 5 이내)
        given(jobCountJpaRepository.findAllKeys()).willReturn(List.of(
                new JobCountKeyRow("11110", "011"),
                new JobCountKeyRow("11140", "012")));
        StubProvider provider = new StubProvider(List.of(page(1, 1, posting("A", "11110", "011"))));
        WorknetJobCountItemReader reader = new WorknetJobCountItemReader(
                provider, jobCountJpaRepository, 100, 1000, 5, true, true);

        // when
        List<JobCountCsvRow> rows = readAll(reader);

        // then - 예산에 걸리지 않았으므로 정상 반영 + 사라진 조합 0 리셋
        assertThat(provider.calls()).isEqualTo(1);
        assertThat(rows).extracting(JobCountCsvRow::sigunguCode, JobCountCsvRow::middleCode, JobCountCsvRow::count)
                .containsExactlyInAnyOrder(
                        tuple("11110", "011", 1),
                        tuple("11140", "012", 0));
    }

    @Test
    @DisplayName("인증키가 없으면 API 를 부르지 않고 아무 행도 내보내지 않는다")
    void emitsNothingAndSkipsApiWhenKeyMissing() throws Exception {
        // given
        StubProvider provider = new StubProvider(List.of(page(1, 1, posting("A", "11110", "011"))));
        provider.configured = false;

        // when
        List<JobCountCsvRow> rows = readAll(reader(provider, 100, true, true));

        // then - 0 리셋도 하지 않는다. 기존 데이터를 지우면 안 된다
        assertThat(rows).isEmpty();
        assertThat(provider.calls()).isZero();
        then(jobCountJpaRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("매핑 실패 건수는 공급자가 알려준 대로 누적된다")
    void accumulatesUnresolvedCountsReportedByProvider() throws Exception {
        // given
        StubProvider provider = new StubProvider(List.of(
                new JobPostingPage(1, 1, List.of(posting("A", "11110", "011")), 3, 5)));

        // when
        WorknetJobCountItemReader reader = reader(provider, 100, true, false);
        List<JobCountCsvRow> rows = readAll(reader);

        // then - 버려진 공고는 집계에 들어가지 않는다
        assertThat(rows).singleElement().extracting(JobCountCsvRow::count).isEqualTo(1);
    }

    private WorknetJobCountItemReader reader(JobPostingProvider provider,
                                             int pageSize,
                                             boolean dedupe,
                                             boolean resetMissing) {
        return new WorknetJobCountItemReader(provider, jobCountJpaRepository, pageSize, 1000, 100_000, dedupe, resetMissing);
    }

    private List<JobCountCsvRow> readAll(WorknetJobCountItemReader reader) throws Exception {
        reader.open(new ExecutionContext());
        List<JobCountCsvRow> rows = new ArrayList<>();
        JobCountCsvRow row;
        while ((row = reader.read()) != null) {
            rows.add(row);
        }
        reader.close();
        return rows;
    }

    private static JobPostingPage page(int pageNumber, int total, JobPosting... postings) {
        return new JobPostingPage(pageNumber, total, List.of(postings), 0, 0);
    }

    private static JobPosting posting(String id, String sigungu, String jobCode) {
        return new JobPosting(JobPostingId.of(id),
                Set.of(SigunguCode.of(sigungu)),
                Set.of(JobCode.of(jobCode)));
    }

    /** 미리 준비한 페이지를 순서대로 돌려주는 인메모리 공급자. 실제 HTTP 를 타지 않는다. */
    private static final class StubProvider implements JobPostingProvider {

        private final Map<Integer, JobPostingPage> pages;
        private final AtomicInteger calls = new AtomicInteger();
        private boolean configured = true;

        private StubProvider(List<JobPostingPage> pages) {
            this.pages = new java.util.LinkedHashMap<>();
            pages.forEach(page -> this.pages.put(page.pageNumber(), page));
        }

        @Override
        public JobPostingPage fetchPage(int pageNumber, int pageSize) {
            calls.incrementAndGet();
            return pages.getOrDefault(pageNumber, JobPostingPage.empty(pageNumber));
        }

        @Override
        public boolean isConfigured() {
            return configured;
        }

        @Override
        public int maxPageSize() {
            return 100;
        }

        private int calls() {
            return calls.get();
        }
    }
}
