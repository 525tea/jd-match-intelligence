package jobflow.domain.job;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobRepository extends JpaRepository<Job, Long> {

    List<Job> findAllByOrderByCreatedAtDesc();

    List<Job> findByStatusAndDeadlineAtBefore(JobStatus status, LocalDateTime deadlineAt);

    boolean existsBySourceAndExternalId(String source, String externalId);

    Optional<Job> findBySourceAndExternalId(String source, String externalId);

    @Query(
            value = """
                    SELECT *
                    FROM jobs
                    WHERE status = 'OPEN'
                      AND MATCH(title, company_name, description, role_detail, industry, location_region, location_city)
                          AGAINST (:keyword IN NATURAL LANGUAGE MODE)
                    ORDER BY MATCH(title, company_name, description, role_detail, industry, location_region, location_city)
                          AGAINST (:keyword IN NATURAL LANGUAGE MODE) DESC,
                          created_at DESC
                    LIMIT :limit
                    """,
            nativeQuery = true
    )
    List<Job> searchOpenJobsByFullText(
            @Param("keyword") String keyword,
            @Param("limit") int limit
    );
}
