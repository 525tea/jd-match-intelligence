package jobflow.domain.job.search;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.util.List;
import jobflow.domain.job.Job;
import jobflow.domain.job.JobRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

class JobSearchReindexRunnerTest {

    private final JobRepository jobRepository = org.mockito.Mockito.mock(JobRepository.class);
    private final JobSearchIndexingService jobSearchIndexingService = org.mockito.Mockito.mock(JobSearchIndexingService.class);

    @Test
    @DisplayName("id 오름차순 batch로 전체 공고를 검색 index에 재색인한다")
    void run() {
        JobSearchReindexRunner runner = new JobSearchReindexRunner(
                jobRepository,
                jobSearchIndexingService
        );
        ReflectionTestUtils.setField(runner, "batchSize", 2);

        Job firstJob = org.mockito.Mockito.mock(Job.class);
        Job secondJob = org.mockito.Mockito.mock(Job.class);
        Job thirdJob = org.mockito.Mockito.mock(Job.class);

        given(firstJob.getId()).willReturn(1L);
        given(secondJob.getId()).willReturn(2L);
        given(thirdJob.getId()).willReturn(3L);

        given(jobRepository.findByIdGreaterThanOrderByIdAsc(eq(0L), any(PageRequest.class)))
                .willReturn(List.of(firstJob, secondJob));
        given(jobRepository.findByIdGreaterThanOrderByIdAsc(eq(2L), any(PageRequest.class)))
                .willReturn(List.of(thirdJob));
        given(jobRepository.findByIdGreaterThanOrderByIdAsc(eq(3L), any(PageRequest.class)))
                .willReturn(List.of());

        runner.run(new DefaultApplicationArguments());

        verify(jobRepository).findByIdGreaterThanOrderByIdAsc(0L, PageRequest.of(0, 2));
        verify(jobRepository).findByIdGreaterThanOrderByIdAsc(2L, PageRequest.of(0, 2));
        verify(jobRepository).findByIdGreaterThanOrderByIdAsc(3L, PageRequest.of(0, 2));
        verify(jobSearchIndexingService).indexAll(List.of(firstJob, secondJob));
        verify(jobSearchIndexingService).indexAll(List.of(thirdJob));
        verify(jobSearchIndexingService).refresh();
    }
}
