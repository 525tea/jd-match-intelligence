package jobflow.domain.project;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserProjectRepository extends JpaRepository<UserProject, Long> {

    boolean existsByIdAndUserId(Long id, Long userId);

    Optional<UserProject> findByIdAndUserId(Long id, Long userId);
}
