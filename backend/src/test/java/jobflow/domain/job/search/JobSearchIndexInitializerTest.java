package jobflow.domain.job.search;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

class JobSearchIndexInitializerTest {

    @Test
    @DisplayName("애플리케이션 시작 시 검색 index 생성을 위임한다")
    void run() {
        JobSearchIndexService jobSearchIndexService = org.mockito.Mockito.mock(JobSearchIndexService.class);
        JobSearchIndexInitializer initializer = new JobSearchIndexInitializer(jobSearchIndexService);

        initializer.run(new DefaultApplicationArguments());

        verify(jobSearchIndexService).createIndexIfMissing();
    }
}
