package jobflow.domain.analytics;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SkillCooccurrenceRepository extends JpaRepository<SkillCooccurrence, Long> {

    List<SkillCooccurrence> findByPeriodTypeAndPeriodStartAndBaseSkillIdOrderByLiftScoreDesc(
            AnalyticsPeriodType periodType,
            LocalDate periodStart,
            Long baseSkillId
    );
}
