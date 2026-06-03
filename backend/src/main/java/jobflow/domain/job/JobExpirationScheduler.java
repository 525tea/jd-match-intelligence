package jobflow.domain.job;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JobExpirationScheduler {

    private final JobExpirationService jobExpirationService;

    @Scheduled(
            fixedDelayString = "${jobflow.job-expiration.fixed-delay:60000}",
            initialDelayString = "${jobflow.job-expiration.initial-delay:60000}"
    )
    public void expireOverdueJobs() {
        jobExpirationService.expireOverdueJobs();
    }
}
