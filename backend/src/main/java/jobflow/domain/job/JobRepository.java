package jobflow.domain.job;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobRepository extends JpaRepository<Job, Long> {

    List<Job> findAllByOrderByCreatedAtDesc();

    boolean existsBySourceAndExternalId(String source, String externalId);
}
