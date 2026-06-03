package jobflow.domain.job;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class JobExpirationSchedulerTest {

    @Test
    @DisplayName("스케줄러는 만료 서비스에 처리를 위임한다")
    void expireOverdueJobs() {
        JobExpirationService jobExpirationService = mock(JobExpirationService.class);
        JobExpirationScheduler scheduler = new JobExpirationScheduler(jobExpirationService);

        scheduler.expireOverdueJobs();

        verify(jobExpirationService).expireOverdueJobs();
    }
}
