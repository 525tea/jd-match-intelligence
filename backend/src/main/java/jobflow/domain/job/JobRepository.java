package jobflow.domain.job;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobRepository extends JpaRepository<Job, Long> {

    List<Job> findAllByOrderByCreatedAtDesc();

    List<Job> findByStatusAndDeadlineAtBefore(JobStatus status, LocalDateTime deadlineAt);

    boolean existsBySourceAndExternalId(String source, String externalId);
}
