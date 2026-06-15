package jobflow.domain.userjob;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserJobRepository extends JpaRepository<UserJob, Long> {

    Optional<UserJob> findByUserIdAndJobId(Long userId, Long jobId);

    List<UserJob> findByUserIdAndStatusOrderByUpdatedAtDesc(Long userId, UserJobStatus status);

    List<UserJob> findByUserIdAndJobIdIn(Long userId, Collection<Long> jobIds);

    boolean existsByUserIdAndJobId(Long userId, Long jobId);
}
