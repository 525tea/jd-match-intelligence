package jobflow.domain.userjob;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserJobRepository extends JpaRepository<UserJob, Long> {

    Optional<UserJob> findByUserIdAndJobId(Long userId, Long jobId);

    boolean existsByUserIdAndJobId(Long userId, Long jobId);
}
