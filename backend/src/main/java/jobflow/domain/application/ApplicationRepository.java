package jobflow.domain.application;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationRepository extends JpaRepository<Application, Long> {

    boolean existsByUserIdAndJobId(Long userId, Long jobId);

    Optional<Application> findByIdAndUserId(Long id, Long userId);

    List<Application> findByUserIdOrderByCreatedAtDesc(Long userId);
}
