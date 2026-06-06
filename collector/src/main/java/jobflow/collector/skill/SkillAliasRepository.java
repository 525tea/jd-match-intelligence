package jobflow.collector.skill;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SkillAliasRepository extends JpaRepository<SkillAlias, Long> {

    List<SkillAlias> findByEnabledTrueOrderByNormalizedAliasAsc();
}
