package jobflow.domain.analytics;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SkillExperienceMarketRepository extends JpaRepository<SkillExperienceMarket, Long> {

    List<SkillExperienceMarket> findByPeriodTypeAndPeriodStartAndSkillIdOrderByLiftScoreDesc(
            AnalyticsPeriodType periodType,
            LocalDate periodStart,
            Long skillId
    );
}
