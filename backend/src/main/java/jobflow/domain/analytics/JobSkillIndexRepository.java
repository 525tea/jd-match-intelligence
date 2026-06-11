package jobflow.domain.analytics;

import jobflow.domain.job.JobRole;
import jobflow.domain.job.RequirementType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface JobSkillIndexRepository extends JpaRepository<JobSkillIndex, Long> {

    long countByRequirementType(RequirementType requirementType);

    @Query("""
            SELECT new jobflow.domain.analytics.JobSkillMatchSummary(
                jsi.job.id,
                jsi.job.title,
                jsi.job.companyName,
                jsi.job.role,
                jsi.job.careerLevel,
                SUM(CASE
                    WHEN jsi.requirementType = jobflow.domain.job.RequirementType.REQUIRED
                    THEN 1 ELSE 0
                END),
                SUM(CASE
                    WHEN jsi.requirementType = jobflow.domain.job.RequirementType.REQUIRED
                     AND jsi.skill.id IN :skillIds
                    THEN 1 ELSE 0
                END),
                SUM(CASE
                    WHEN jsi.requirementType = jobflow.domain.job.RequirementType.PREFERRED
                    THEN 1 ELSE 0
                END),
                SUM(CASE
                    WHEN jsi.requirementType = jobflow.domain.job.RequirementType.PREFERRED
                     AND jsi.skill.id IN :skillIds
                    THEN 1 ELSE 0
                END)
            )
            FROM JobSkillIndex jsi
            WHERE jsi.job.status = jobflow.domain.job.JobStatus.OPEN
              AND jsi.job.role IN :targetRoles
            GROUP BY
                jsi.job.id,
                jsi.job.title,
                jsi.job.companyName,
                jsi.job.role,
                jsi.job.careerLevel
            ORDER BY
                SUM(CASE
                    WHEN jsi.requirementType = jobflow.domain.job.RequirementType.REQUIRED
                     AND jsi.skill.id IN :skillIds
                    THEN 1 ELSE 0
                END) DESC,
                SUM(CASE
                    WHEN jsi.requirementType = jobflow.domain.job.RequirementType.PREFERRED
                     AND jsi.skill.id IN :skillIds
                    THEN 1 ELSE 0
                END) DESC,
                SUM(CASE
                    WHEN jsi.requirementType = jobflow.domain.job.RequirementType.REQUIRED
                    THEN 1 ELSE 0
                END) ASC,
                jsi.job.id DESC
            """)
    List<JobSkillMatchSummary> findOpenJobSkillMatchSummaries(
            @Param("skillIds") Collection<Long> skillIds,
            @Param("targetRoles") Collection<JobRole> targetRoles,
            Pageable pageable
    );
}
