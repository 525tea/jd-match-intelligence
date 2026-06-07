package jobflow.domain.analytics;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

public interface SkillTrendRepository extends JpaRepository<SkillTrend, Long> {

    List<SkillTrend> findByPeriodTypeAndPeriodStartOrderByTrendScoreDesc(
            AnalyticsPeriodType periodType,
            LocalDate periodStart
    );

    @Modifying
    void deleteByPeriodTypeAndPeriodStart(
            AnalyticsPeriodType periodType,
            LocalDate periodStart
    );
}
