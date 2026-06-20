package jobflow.collector.normalization;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jobflow.collector.common.BaseTimeEntity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "normalization_candidates")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NormalizationCandidate extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "candidate_type", nullable = false, length = 40)
    private NormalizationCandidateType type;

    @Column(nullable = false, length = 50)
    private String source;

    @Column(nullable = false, length = 150)
    private String value;

    @Column(nullable = false, length = 150)
    private String normalizedValue;

    @Column(nullable = false)
    private int occurrenceCount;

    private Long firstSeenJobId;

    private Long lastSeenJobId;

    private Long sampleJobId;

    @Column(length = 255)
    private String sampleJobTitle;

    @Column(length = 1000)
    private String sampleContext;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NormalizationCandidateStatus status;

    public static NormalizationCandidate firstSeen(
            NormalizationCandidateType type,
            String source,
            String value,
            String normalizedValue,
            Long jobId,
            String jobTitle,
            String sampleContext
    ) {
        NormalizationCandidate candidate = new NormalizationCandidate();
        candidate.type = type;
        candidate.source = source;
        candidate.value = value;
        candidate.normalizedValue = normalizedValue;
        candidate.occurrenceCount = 1;
        candidate.firstSeenJobId = jobId;
        candidate.lastSeenJobId = jobId;
        candidate.sampleJobId = jobId;
        candidate.sampleJobTitle = truncate(jobTitle, 255);
        candidate.sampleContext = truncate(sampleContext, 1000);
        candidate.status = NormalizationCandidateStatus.PENDING;
        return candidate;
    }

    public void recordOccurrence(
            Long jobId,
            String value,
            String jobTitle,
            String sampleContext
    ) {
        this.value = truncate(value, 150);

        if (this.lastSeenJobId == null || !this.lastSeenJobId.equals(jobId)) {
            this.occurrenceCount++;
        }

        this.lastSeenJobId = jobId;

        if (this.sampleContext == null || this.sampleContext.isBlank()) {
            this.sampleJobId = jobId;
            this.sampleJobTitle = truncate(jobTitle, 255);
            this.sampleContext = truncate(sampleContext, 1000);
        }
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }

        if (value.length() <= maxLength) {
            return value;
        }

        return value.substring(0, maxLength);
    }
}
