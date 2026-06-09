package jobflow.collector.job;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobRepository extends JpaRepository<Job, Long> {

    List<Job> findAllByOrderByCreatedAtDesc();

    List<Job> findByStatusAndDeadlineAtBefore(JobStatus status, LocalDateTime deadlineAt);

    boolean existsBySourceAndExternalId(String source, String externalId);

    Optional<Job> findBySourceAndExternalId(String source, String externalId);

    List<Job> findByCanonicalFingerprintAndSourceNot(String canonicalFingerprint, String source);

    List<Job> findBySourceInOrderByIdAsc(Collection<String> sources);
}
