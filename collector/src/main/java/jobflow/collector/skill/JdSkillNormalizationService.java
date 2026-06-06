package jobflow.collector.skill;

import static java.util.stream.Collectors.joining;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JdSkillNormalizationService {

    private static final BigDecimal CANONICAL_SKILL_CONFIDENCE = BigDecimal.ONE;

    private final SkillRepository skillRepository;
    private final SkillAliasRepository skillAliasRepository;

    public List<NormalizedSkillMatch> normalize(String... texts) {
        String normalizedText = normalizeText(texts);

        if (normalizedText.isBlank()) {
            return List.of();
        }

        Map<String, NormalizedSkillMatch> matches = new LinkedHashMap<>();

        for (Skill skill : skillRepository.findAllByOrderByNameAsc()) {
            addMatchIfPresent(
                    matches,
                    normalizedText,
                    skill,
                    skill.getName(),
                    skill.getNormalizedName(),
                    CANONICAL_SKILL_CONFIDENCE
            );
        }

        for (SkillAlias skillAlias : skillAliasRepository.findByEnabledTrueOrderByNormalizedAliasAsc()) {
            addMatchIfPresent(
                    matches,
                    normalizedText,
                    skillAlias.getSkill(),
                    skillAlias.getAlias(),
                    skillAlias.getNormalizedAlias(),
                    skillAlias.getConfidence()
            );
        }

        return matches.values()
                .stream()
                .sorted(Comparator.comparing(match -> match.skill().getName()))
                .toList();
    }

    private void addMatchIfPresent(
            Map<String, NormalizedSkillMatch> matches,
            String normalizedText,
            Skill skill,
            String sourceAlias,
            String normalizedAlias,
            BigDecimal confidence
    ) {
        String normalizedCandidate = normalizeText(normalizedAlias);

        if (!containsAlias(normalizedText, normalizedCandidate)) {
            return;
        }

        String skillKey = skill.getNormalizedName();
        NormalizedSkillMatch current = matches.get(skillKey);
        NormalizedSkillMatch candidate = new NormalizedSkillMatch(
                skill,
                sourceAlias,
                normalizedCandidate,
                confidence
        );

        if (current == null || isBetter(candidate, current)) {
            matches.put(skillKey, candidate);
        }
    }

    private boolean isBetter(NormalizedSkillMatch candidate, NormalizedSkillMatch current) {
        int confidenceComparison = candidate.confidence().compareTo(current.confidence());

        if (confidenceComparison != 0) {
            return confidenceComparison > 0;
        }

        return candidate.normalizedAlias().length() > current.normalizedAlias().length();
    }

    private boolean containsAlias(String normalizedText, String normalizedAlias) {
        if (normalizedAlias.isBlank()) {
            return false;
        }

        if (containsKorean(normalizedAlias)) {
            return normalizedText.contains(normalizedAlias);
        }

        Pattern pattern = Pattern.compile(
                "(?<![a-z0-9+#.])" + Pattern.quote(normalizedAlias) + "(?![a-z0-9+#.])"
        );

        return pattern.matcher(normalizedText).find();
    }

    private boolean containsKorean(String text) {
        return text.chars()
                .anyMatch(character -> Character.UnicodeScript.of(character) == Character.UnicodeScript.HANGUL);
    }

    private String normalizeText(String... texts) {
        if (texts == null || texts.length == 0) {
            return "";
        }

        return Arrays.stream(texts)
                .filter(Objects::nonNull)
                .map(this::normalizeText)
                .filter(text -> !text.isBlank())
                .collect(joining(" "));
    }

    private String normalizeText(String text) {
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);

        return normalized
                .replaceAll("[\\p{Punct}&&[^+#.]]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
