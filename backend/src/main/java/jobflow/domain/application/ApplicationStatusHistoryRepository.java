package jobflow.domain.application;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationStatusHistoryRepository extends JpaRepository<ApplicationStatusHistory, Long> {

    List<ApplicationStatusHistory> findByApplicationIdOrderByChangedAtAsc(Long applicationId);
}
