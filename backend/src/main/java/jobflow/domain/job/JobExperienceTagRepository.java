package jobflow.domain.job;

import java.time.LocalDateTime;
import java.util.List;
import jobflow.domain.analytics.ExperienceTagMarketAggregate;
import jobflow.domain.analytics.JobSkillExperienceMarketAggregate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobExperienceTagRepository extends JpaRepository<JobExperienceTag, Long> {

    List<JobExperienceTag> findByJobId(Long jobId);

    void deleteByJobId(Long jobId);

    @Query("""
            SELECT jet
            FROM JobExperienceTag jet
            JOIN FETCH jet.job job
            JOIN FETCH jet.tagCode tagCode
            WHERE job.id IN :jobIds
            ORDER BY job.id ASC, tagCode.code ASC
            """)
    List<JobExperienceTag> findByJobIdInWithTagCode(@Param("jobIds") List<Long> jobIds);

    @Query("""
            SELECT new jobflow.domain.analytics.JobSkillExperienceMarketAggregate(
                js.skill,
                jet.tagCode,
                COUNT(DISTINCT js.job.id)
            )
            FROM JobSkill js, JobExperienceTag jet
            WHERE js.job.id = jet.job.id
              AND js.job.createdAt >= :from
              AND js.job.createdAt < :to
            GROUP BY js.skill, jet.tagCode
            ORDER BY COUNT(DISTINCT js.job.id) DESC, js.skill.name ASC, jet.tagCode.code ASC
            """)
    List<JobSkillExperienceMarketAggregate> aggregateSkillExperienceMarkets(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    @Query("""
            SELECT new jobflow.domain.analytics.ExperienceTagMarketAggregate(
                jet.tagCode,
                COUNT(DISTINCT jet.job.id)
            )
            FROM JobExperienceTag jet
            WHERE jet.job.createdAt >= :from
              AND jet.job.createdAt < :to
            GROUP BY jet.tagCode
            ORDER BY COUNT(DISTINCT jet.job.id) DESC, jet.tagCode.code ASC
            """)
    List<ExperienceTagMarketAggregate> aggregateExperienceTagMarkets(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );
}
