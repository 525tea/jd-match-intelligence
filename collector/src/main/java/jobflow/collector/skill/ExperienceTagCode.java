package jobflow.collector.skill;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "experience_tag_codes")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExperienceTagCode {

    @Id
    @Column(length = 50)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static ExperienceTagCode create(String code, String name, String description) {
        ExperienceTagCode tagCode = new ExperienceTagCode();
        tagCode.code = code;
        tagCode.name = name;
        tagCode.description = description;
        tagCode.createdAt = LocalDateTime.now();
        return tagCode;
    }
}
