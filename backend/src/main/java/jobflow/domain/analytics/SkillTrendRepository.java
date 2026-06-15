package jobflow.domain.analytics;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface SkillTrendRepository extends JpaRepository<SkillTrend, Long> {

    List<SkillTrend> findByPeriodTypeAndPeriodStartOrderByTrendScoreDesc(
            AnalyticsPeriodType periodType,
            LocalDate periodStart
    );

    @Query("""
            SELECT MAX(st.periodStart)
            FROM SkillTrend st
            WHERE st.periodType = :periodType
            """)
    Optional<LocalDate> findLatestPeriodStartByPeriodType(AnalyticsPeriodType periodType);

    @Query("""
            SELECT st
            FROM SkillTrend st
            JOIN FETCH st.skill
            WHERE st.periodType = :periodType
              AND st.periodStart = :periodStart
              AND st.skill.name IN :skillNames
            """)
    List<SkillTrend> findByPeriodTypeAndPeriodStartAndSkillNameIn(
            AnalyticsPeriodType periodType,
            LocalDate periodStart,
            Collection<String> skillNames
    );

    @Modifying
    void deleteByPeriodTypeAndPeriodStart(
            AnalyticsPeriodType periodType,
            LocalDate periodStart
    );
}
