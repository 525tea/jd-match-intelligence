package jobflow.domain.analytics;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

public interface SkillExperienceMarketRepository extends JpaRepository<SkillExperienceMarket, Long> {

    List<SkillExperienceMarket> findByPeriodTypeAndPeriodStartAndSkillIdOrderByLiftScoreDesc(
            AnalyticsPeriodType periodType,
            LocalDate periodStart,
            Long skillId
    );

    @Modifying
    void deleteByPeriodTypeAndPeriodStart(
            AnalyticsPeriodType periodType,
            LocalDate periodStart
    );
}
