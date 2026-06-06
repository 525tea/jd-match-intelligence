package jobflow.collector.skill;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "skills")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Skill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 100)
    private String normalizedName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SkillCategory category;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static Skill create(String name, String normalizedName, SkillCategory category) {
        Skill skill = new Skill();
        skill.name = name;
        skill.normalizedName = normalizedName;
        skill.category = category;
        return skill;
    }
}
