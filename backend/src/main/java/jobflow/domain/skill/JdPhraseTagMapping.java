package jobflow.domain.skill;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "jd_phrase_tag_mapping")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JdPhraseTagMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String phrase;

    @Column(nullable = false, length = 200)
    private String normalizedPhrase;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tag_code", nullable = false)
    private ExperienceTagCode tagCode;

    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal confidence;

    @Column(nullable = false)
    private boolean enabled;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static JdPhraseTagMapping create(
            String phrase,
            String normalizedPhrase,
            ExperienceTagCode tagCode,
            BigDecimal confidence
    ) {
        JdPhraseTagMapping mapping = new JdPhraseTagMapping();
        mapping.phrase = phrase;
        mapping.normalizedPhrase = normalizedPhrase;
        mapping.tagCode = tagCode;
        mapping.confidence = confidence;
        mapping.enabled = true;
        return mapping;
    }

    public void disable() {
        this.enabled = false;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
