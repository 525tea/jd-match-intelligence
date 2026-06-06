package jobflow.domain.skill;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JdPhraseTagMappingRepository extends JpaRepository<JdPhraseTagMapping, Long> {

    List<JdPhraseTagMapping> findByEnabledTrueOrderByNormalizedPhraseAsc();

    boolean existsByNormalizedPhraseAndTagCodeCode(String normalizedPhrase, String tagCode);
}
