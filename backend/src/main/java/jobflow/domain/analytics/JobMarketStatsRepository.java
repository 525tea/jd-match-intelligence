package jobflow.domain.analytics;

import java.time.LocalDate;
import java.util.List;
import jobflow.domain.job.JobRole;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobMarketStatsRepository extends JpaRepository<JobMarketStats, Long> {

    List<JobMarketStats> findByPeriodTypeAndPeriodStartAndRoleOrderByJobCountDesc(
            AnalyticsPeriodType periodType,
            LocalDate periodStart,
            JobRole role
    );
}
