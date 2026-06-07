package jobflow.domain.analytics;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SkillTrendRepository extends JpaRepository<SkillTrend, Long> {

    List<SkillTrend> findByPeriodTypeAndPeriodStartOrderByTrendScoreDesc(
            AnalyticsPeriodType periodType,
            LocalDate periodStart
    );
}
