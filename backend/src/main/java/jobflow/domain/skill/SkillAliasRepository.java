package jobflow.domain.skill;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SkillAliasRepository extends JpaRepository<SkillAlias, Long> {

    Optional<SkillAlias> findByNormalizedAliasAndEnabledTrue(String normalizedAlias);

    List<SkillAlias> findByEnabledTrueOrderByNormalizedAliasAsc();

    boolean existsByNormalizedAlias(String normalizedAlias);
}
