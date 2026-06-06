package jobflow.collector.skill;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "skill_aliases")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SkillAlias {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "skill_id", nullable = false)
    private Skill skill;

    @Column(nullable = false, length = 100)
    private String alias;

    @Column(nullable = false, unique = true, length = 100)
    private String normalizedAlias;

    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal confidence;

    @Column(nullable = false)
    private boolean enabled;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static SkillAlias create(
            Skill skill,
            String alias,
            String normalizedAlias,
            BigDecimal confidence
    ) {
        SkillAlias skillAlias = new SkillAlias();
        skillAlias.skill = skill;
        skillAlias.alias = alias;
        skillAlias.normalizedAlias = normalizedAlias;
        skillAlias.confidence = confidence;
        skillAlias.enabled = true;
        return skillAlias;
    }
}
