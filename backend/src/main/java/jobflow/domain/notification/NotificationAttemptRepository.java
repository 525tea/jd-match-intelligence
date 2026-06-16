package jobflow.domain.notification;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationAttemptRepository extends JpaRepository<NotificationAttempt, Long> {

    List<NotificationAttempt> findByNotificationLogIdOrderByAttemptNumberAsc(Long notificationLogId);
}
