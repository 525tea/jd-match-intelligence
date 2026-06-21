package jobflow.domain.application;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import jobflow.domain.common.BaseTimeEntity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "application_status_histories")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApplicationStatusHistory extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 30)
    private ApplicationStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "next_status", nullable = false, length = 30)
    private ApplicationStatus nextStatus;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    public static ApplicationStatusHistory record(
            Application application,
            ApplicationStatus previousStatus,
            ApplicationStatus nextStatus,
            LocalDateTime changedAt
    ) {
        ApplicationStatusHistory history = new ApplicationStatusHistory();
        history.application = application;
        history.previousStatus = previousStatus;
        history.nextStatus = nextStatus;
        history.changedAt = changedAt;
        return history;
    }
}
