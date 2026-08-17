package SDD.smash.domain.job.application;

import SDD.smash.domain.job.application.dto.JobVacancyView;
import SDD.smash.domain.job.domain.model.JobPostingId;
import SDD.smash.domain.job.domain.model.JobVacancy;
import SDD.smash.domain.job.domain.port.JobVacancyCache;
import SDD.smash.domain.job.domain.port.JobVacancyProvider;
import SDD.smash.global.domain.model.SigunguCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class JobVacancyQueryServiceTest {

    @Mock JobVacancyProvider jobVacancyProvider;
    @Mock JobVacancyCache jobVacancyCache;

    private JobVacancyQueryService service() {
        return new JobVacancyQueryService(jobVacancyProvider, jobVacancyCache, 5, Duration.ofSeconds(3));
    }

    private JobVacancy vacancy(String id, String title) {
        return new JobVacancy(JobPostingId.of(id), title, "회사", "url", "지역", "직종",
                null, null, null, null, true, null, null);
    }

    @Test
    @DisplayName("캐시가 있으면 공급자를 호출하지 않는다")
    void returnsCachedWithoutCallingProvider() {
        // given
        given(jobVacancyCache.find(any()))
                .willReturn(Optional.of(List.of(vacancy("1", "백엔드 개발자"))));

        // when
        List<JobVacancyView> result = service().getVacancies(SigunguCode.of("11680"));

        // then
        assertThat(result).extracting(JobVacancyView::title).containsExactly("백엔드 개발자");
        then(jobVacancyProvider).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("캐시 미스면 공급자를 호출하고 결과를 캐시에 저장한다")
    void fetchesAndCachesOnMiss() {
        // given
        given(jobVacancyCache.find(any())).willReturn(Optional.empty());
        given(jobVacancyProvider.findVacancies(any(), anyInt()))
                .willReturn(Optional.of(List.of(vacancy("1", "QA 엔지니어"))));

        // when
        List<JobVacancyView> result = service().getVacancies(SigunguCode.of("11680"));

        // then
        assertThat(result).hasSize(1);
        then(jobVacancyCache).should().put(any(), any());
    }

    @Test
    @DisplayName("공급자가 미시도(Optional.empty)면 캐시에 저장하지 않고 빈 목록을 돌려준다")
    void doesNotCacheWhenProviderNotAttempted() {
        // given - access-key 미설정/지역 미매핑 시 공급자가 미시도(Optional.empty)를 준다
        given(jobVacancyCache.find(any())).willReturn(Optional.empty());
        given(jobVacancyProvider.findVacancies(any(), anyInt())).willReturn(Optional.empty());

        // when
        List<JobVacancyView> result = service().getVacancies(SigunguCode.of("11680"));

        // then
        assertThat(result).isEmpty();
        then(jobVacancyCache).should(org.mockito.Mockito.never()).put(any(), any());
    }

    @Test
    @DisplayName("공급자가 실제 0건(Optional.of 빈 목록)을 주면 네거티브 캐싱한다")
    void negativeCachesRealEmptyResult() {
        // given - 실제 조회를 시도해 0건이 나온 경우
        given(jobVacancyCache.find(any())).willReturn(Optional.empty());
        given(jobVacancyProvider.findVacancies(any(), anyInt())).willReturn(Optional.of(List.of()));

        // when
        List<JobVacancyView> result = service().getVacancies(SigunguCode.of("11680"));

        // then - 빈 목록을 put(어댑터가 짧은 TTL 로 네거티브 캐싱)
        assertThat(result).isEmpty();
        then(jobVacancyCache).should().put(any(), any());
    }

    @Test
    @DisplayName("캐시된 0건(네거티브 히트)이면 공급자를 다시 호출하지 않는다")
    void negativeHitSkipsProvider() {
        // given - 캐시에 '빈 목록'이 존재(네거티브 히트)
        given(jobVacancyCache.find(any())).willReturn(Optional.of(List.of()));

        // when
        List<JobVacancyView> result = service().getVacancies(SigunguCode.of("11680"));

        // then
        assertThat(result).isEmpty();
        then(jobVacancyProvider).shouldHaveNoInteractions();
    }
}
