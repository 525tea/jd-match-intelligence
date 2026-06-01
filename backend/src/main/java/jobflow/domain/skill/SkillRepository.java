package jobflow.domain.skill;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SkillRepository extends JpaRepository<Skill, Long> {

    boolean existsByName(String name);

    boolean existsByNormalizedName(String normalizedName);

    boolean existsByNameAndIdNot(String name, Long id);

    boolean existsByNormalizedNameAndIdNot(String normalizedName, Long id);

    List<Skill> findAllByOrderByNameAsc();

    List<Skill> findByCategoryOrderByNameAsc(SkillCategory category);

    List<Skill> findByNameContainingIgnoreCaseOrderByNameAsc(String keyword);

    List<Skill> findByCategoryAndNameContainingIgnoreCaseOrderByNameAsc(
            SkillCategory category,
            String keyword
    );
}
