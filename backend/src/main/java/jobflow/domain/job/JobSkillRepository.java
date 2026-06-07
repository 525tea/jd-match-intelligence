package jobflow.domain.job;

import java.time.LocalDateTime;
import java.util.List;
import jobflow.domain.analytics.JobSkillTrendAggregate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobSkillRepository extends JpaRepository<JobSkill, Long> {

    List<JobSkill> findByJobId(Long jobId);

    void deleteByJobId(Long jobId);

    @Query("""
            SELECT new jobflow.domain.analytics.JobSkillTrendAggregate(
                js.skill,
                COUNT(DISTINCT js.job.id),
                SUM(CASE WHEN js.requirementType = jobflow.domain.job.RequirementType.REQUIRED THEN 1 ELSE 0 END),
                SUM(CASE WHEN js.requirementType = jobflow.domain.job.RequirementType.PREFERRED THEN 1 ELSE 0 END)
            )
            FROM JobSkill js
            WHERE js.job.createdAt >= :from
              AND js.job.createdAt < :to
            GROUP BY js.skill
            ORDER BY COUNT(DISTINCT js.job.id) DESC, js.skill.name ASC
            """)
    List<JobSkillTrendAggregate> aggregateSkillTrends(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );
}
