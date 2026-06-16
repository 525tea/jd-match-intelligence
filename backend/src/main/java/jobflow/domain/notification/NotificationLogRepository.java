package jobflow.domain.notification;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {

    Optional<NotificationLog> findByUserIdAndJobIdAndType(
            Long userId,
            Long jobId,
            NotificationType type
    );

    boolean existsByUserIdAndJobIdAndType(Long userId, Long jobId, NotificationType type);

    List<NotificationLog> findByTypeAndStatusAndNextRetryAtLessThanEqual(
            NotificationType type,
            NotificationStatus status,
            LocalDateTime nextRetryAt
    );
}
