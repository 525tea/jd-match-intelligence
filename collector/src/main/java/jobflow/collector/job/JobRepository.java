package jobflow.collector.job;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface JobRepository extends JpaRepository<Job, Long> {

    List<Job> findAllByOrderByCreatedAtDesc();

    List<Job> findByStatusAndDeadlineAtBefore(JobStatus status, LocalDateTime deadlineAt);

    boolean existsBySourceAndExternalId(String source, String externalId);

    Optional<Job> findBySourceAndExternalId(String source, String externalId);
}
