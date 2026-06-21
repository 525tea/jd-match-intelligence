package jobflow.domain.job;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import jobflow.domain.analytics.JobMarketStatsAggregate;
import org.springframework.data.domain.Pageable;

@Repository("jobDomainRepository")
public interface JobRepository extends JpaRepository<Job, Long> {

    List<Job> findByIdGreaterThanOrderByIdAsc(Long id, Pageable pageable);

    List<Job> findByIdIn(Collection<Long> ids);

    List<Job> findByCanonicalFingerprintOrderByCreatedAtDescIdDesc(String canonicalFingerprint);

    List<Job> findByStatusAndDeadlineAtBefore(JobStatus status, LocalDateTime deadlineAt);

    List<Job> findByStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDescIdDesc(
            JobStatus status,
            LocalDateTime from,
            LocalDateTime to,
            Pageable pageable
    );

    @Query("""
        SELECT job
        FROM Job job
        WHERE (:status IS NULL OR job.status = :status)
          AND (:role IS NULL OR job.role = :role)
          AND (:careerLevel IS NULL OR job.careerLevel = :careerLevel)
          AND (:locationRegion IS NULL OR job.locationRegion = :locationRegion)
          AND (:remoteType IS NULL OR job.remoteType = :remoteType)
        ORDER BY job.createdAt DESC, job.id DESC
        """)
    List<Job> findSummaries(
            @Param("status") JobStatus status,
            @Param("role") JobRole role,
            @Param("careerLevel") CareerLevel careerLevel,
            @Param("locationRegion") String locationRegion,
            @Param("remoteType") RemoteType remoteType,
            Pageable pageable
    );

    boolean existsBySourceAndExternalId(String source, String externalId);

    Optional<Job> findBySourceAndExternalId(String source, String externalId);

    long countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            LocalDateTime from,
            LocalDateTime to
    );

    @Query("""
        SELECT new jobflow.domain.analytics.JobMarketStatsAggregate(
            job.role,
            job.careerLevel,
            job.locationRegion,
            job.remoteType,
            COUNT(job.id),
            SUM(CASE WHEN job.status = jobflow.domain.job.JobStatus.OPEN THEN 1L ELSE 0L END),
            SUM(CASE WHEN job.status = jobflow.domain.job.JobStatus.CLOSED THEN 1L ELSE 0L END),
            SUM(CASE WHEN job.status = jobflow.domain.job.JobStatus.EXPIRED THEN 1L ELSE 0L END),
            AVG(job.minExperienceYears),
            AVG(job.maxExperienceYears)
        )
        FROM Job job
        WHERE job.createdAt >= :from
          AND job.createdAt < :to
        GROUP BY
            job.role,
            job.careerLevel,
            job.locationRegion,
            job.remoteType
        ORDER BY COUNT(job.id) DESC, job.role ASC, job.careerLevel ASC
        """)
    List<JobMarketStatsAggregate> aggregateJobMarketStats(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    @Query(
            value = """
                SELECT
                    id AS id,
                    source AS source,
                    external_id AS externalId,
                    canonical_fingerprint AS canonicalFingerprint,
                    title AS title,
                    company_name AS companyName,
                    role AS role,
                    career_level AS careerLevel,
                    employment_type AS employmentType,
                    location_region AS locationRegion,
                    location_city AS locationCity,
                    remote_type AS remoteType,
                    deadline_at AS deadlineAt,
                    status AS status,
                    MATCH(title, company_name, description, role_detail, industry, location_region, location_city)
                        AGAINST (:keyword IN NATURAL LANGUAGE MODE) AS score
                FROM jobs
                WHERE status = 'OPEN'
                  AND MATCH(title, company_name, description, role_detail, industry, location_region, location_city)
                      AGAINST (:keyword IN NATURAL LANGUAGE MODE)
                ORDER BY score DESC, created_at DESC
                LIMIT :limit
                """,
            nativeQuery = true
    )
    List<JobSearchProjection> searchOpenJobsByFullText(
            @Param("keyword") String keyword,
            @Param("limit") int limit
    );
}
