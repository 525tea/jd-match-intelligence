package jobflow.collector.skill;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JdExperienceTagNormalizationService {

    private final JdPhraseTagMappingRepository jdPhraseTagMappingRepository;

    public List<NormalizedExperienceTagMatch> normalize(String... texts) {
        String normalizedText = normalizeText(texts);
        if (normalizedText.isBlank()) {
            return List.of();
        }

        Map<String, NormalizedExperienceTagMatch> matchesByTagCode = new LinkedHashMap<>();

        for (JdPhraseTagMapping mapping : jdPhraseTagMappingRepository.findByEnabledTrueOrderByNormalizedPhraseAsc()) {
            String phrase = normalizeText(mapping.getNormalizedPhrase());
            if (phrase.isBlank() || !normalizedText.contains(phrase)) {
                continue;
            }

            String tagCode = mapping.getTagCode().getCode();
            NormalizedExperienceTagMatch candidate = new NormalizedExperienceTagMatch(
                    mapping.getTagCode(),
                    mapping.getPhrase(),
                    mapping.getConfidence()
            );

            matchesByTagCode.merge(tagCode, candidate, this::higherConfidence);
        }

        return matchesByTagCode.values().stream()
                .sorted(Comparator.comparing(match -> match.tagCode().getCode()))
                .toList();
    }

    private NormalizedExperienceTagMatch higherConfidence(
            NormalizedExperienceTagMatch current,
            NormalizedExperienceTagMatch candidate
    ) {
        BigDecimal currentConfidence = current.confidence();
        BigDecimal candidateConfidence = candidate.confidence();

        if (candidateConfidence.compareTo(currentConfidence) > 0) {
            return candidate;
        }

        return current;
    }

    private String normalizeText(String... texts) {
        if (texts == null || texts.length == 0) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        for (String text : texts) {
            if (text == null || text.isBlank()) {
                continue;
            }

            builder.append(' ')
                    .append(text.toLowerCase(Locale.ROOT).trim());
        }

        return builder.toString()
                .replaceAll("\\s+", " ")
                .trim();
    }
}
