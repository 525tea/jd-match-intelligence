package jobflow.domain.analytics;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface SkillExperienceMarketRepository extends JpaRepository<SkillExperienceMarket, Long> {

    List<SkillExperienceMarket> findByPeriodTypeAndPeriodStartAndSkillIdOrderByLiftScoreDesc(
            AnalyticsPeriodType periodType,
            LocalDate periodStart,
            Long skillId
    );

    @Query("""
            SELECT MAX(sem.periodStart)
            FROM SkillExperienceMarket sem
            WHERE sem.periodType = :periodType
            """)
    Optional<LocalDate> findLatestPeriodStartByPeriodType(AnalyticsPeriodType periodType);

    @Query("""
            SELECT sem
            FROM SkillExperienceMarket sem
            JOIN FETCH sem.skill
            JOIN FETCH sem.tagCode
            WHERE sem.periodType = :periodType
              AND sem.periodStart = :periodStart
              AND sem.skill.name IN :skillNames
              AND sem.jobCount >= :minJobCount
            ORDER BY sem.jobCount DESC, sem.liftScore DESC, sem.skill.name ASC, sem.tagCode.code ASC
            """)
    List<SkillExperienceMarket> findSupportedMarketsBySkillNameIn(
            AnalyticsPeriodType periodType,
            LocalDate periodStart,
            Collection<String> skillNames,
            long minJobCount,
            Pageable pageable
    );

    @Modifying
    void deleteByPeriodTypeAndPeriodStart(
            AnalyticsPeriodType periodType,
            LocalDate periodStart
    );
}
