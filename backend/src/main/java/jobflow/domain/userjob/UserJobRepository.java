package jobflow.domain.userjob;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserJobRepository extends JpaRepository<UserJob, Long> {

    Optional<UserJob> findByUserIdAndJobId(Long userId, Long jobId);

    List<UserJob> findByUserIdAndStatusOrderByUpdatedAtDesc(Long userId, UserJobStatus status);

    boolean existsByUserIdAndJobId(Long userId, Long jobId);
}
