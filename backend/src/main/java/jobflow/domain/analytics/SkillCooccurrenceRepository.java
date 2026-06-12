package jobflow.domain.analytics;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface SkillCooccurrenceRepository extends JpaRepository<SkillCooccurrence, Long> {

    List<SkillCooccurrence> findByPeriodTypeAndPeriodStartAndBaseSkillIdOrderByLiftScoreDesc(
            AnalyticsPeriodType periodType,
            LocalDate periodStart,
            Long baseSkillId
    );

    @Query("""
            SELECT MAX(sc.periodStart)
            FROM SkillCooccurrence sc
            WHERE sc.periodType = :periodType
            """)
    Optional<LocalDate> findLatestPeriodStartByPeriodType(AnalyticsPeriodType periodType);

    @Query("""
            SELECT sc
            FROM SkillCooccurrence sc
            JOIN FETCH sc.coSkill
            WHERE sc.periodType = :periodType
              AND sc.periodStart = :periodStart
              AND sc.baseSkill.id = :baseSkillId
              AND sc.cooccurrenceCount >= :minCooccurrenceCount
            ORDER BY sc.cooccurrenceCount DESC, sc.liftScore DESC, sc.coSkill.name ASC
            """)
    List<SkillCooccurrence> findSupportedCooccurrences(
            AnalyticsPeriodType periodType,
            LocalDate periodStart,
            Long baseSkillId,
            long minCooccurrenceCount,
            Pageable pageable
    );

    @Modifying
    void deleteByPeriodTypeAndPeriodStart(
            AnalyticsPeriodType periodType,
            LocalDate periodStart
    );
}
