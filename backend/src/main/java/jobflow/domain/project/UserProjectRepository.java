package jobflow.domain.project;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserProjectRepository extends JpaRepository<UserProject, Long> {

    boolean existsByIdAndUserId(Long id, Long userId);
}
