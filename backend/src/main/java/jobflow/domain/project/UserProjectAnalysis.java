package jobflow.domain.project;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "user_project_analysis")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserProjectAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_project_id", nullable = false)
    private UserProject userProject;

    @Column(nullable = false)
    private int analysisVersion = 1;

    @Column(nullable = false, length = 64)
    private String sourceHash;

    @Column(length = 40)
    private String commitSha;

    @Column(length = 100)
    private String modelVersion;

    @Lob
    @Column(columnDefinition = "json")
    private String rawAnalysis;

    @Column(precision = 5, scale = 4)
    private BigDecimal confidenceScore;

    @Column(nullable = false)
    private LocalDateTime analyzedAt;
}
