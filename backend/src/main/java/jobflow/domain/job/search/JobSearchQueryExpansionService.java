package jobflow.domain.job.search;

import jobflow.domain.analytics.AnalyticsPeriodType;
import jobflow.domain.analytics.SkillCooccurrence;
import jobflow.domain.analytics.SkillCooccurrenceRepository;
import jobflow.domain.skill.Skill;
import jobflow.domain.skill.SkillRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class JobSearchQueryExpansionService {

    static final int DEFAULT_MAX_EXPANSIONS = 3;
    static final long DEFAULT_MIN_COOCCURRENCE_COUNT = 3L;

    private static final AnalyticsPeriodType PERIOD_TYPE = AnalyticsPeriodType.MONTHLY;

    private final SkillRepository skillRepository;
    private final SkillCooccurrenceRepository skillCooccurrenceRepository;

    public List<String> expand(String keyword) {
        return expand(keyword, DEFAULT_MAX_EXPANSIONS, DEFAULT_MIN_COOCCURRENCE_COUNT);
    }

    List<String> expand(String keyword, int maxExpansions, long minCooccurrenceCount) {
        if (keyword == null || keyword.isBlank() || maxExpansions <= 0) {
            return List.of();
        }

        Optional<LocalDate> latestPeriodStart =
                skillCooccurrenceRepository.findLatestPeriodStartByPeriodType(PERIOD_TYPE);
        if (latestPeriodStart.isEmpty()) {
            return List.of();
        }

        List<Skill> mentionedSkills = findMentionedSkills(keyword);
        if (mentionedSkills.isEmpty()) {
            return List.of();
        }

        Set<String> expandedSkillNames = new LinkedHashSet<>();
        for (Skill skill : mentionedSkills) {
            if (expandedSkillNames.size() >= maxExpansions) {
                break;
            }

            List<SkillCooccurrence> cooccurrences =
                    skillCooccurrenceRepository.findSupportedCooccurrences(
                            PERIOD_TYPE,
                            latestPeriodStart.get(),
                            skill.getId(),
                            minCooccurrenceCount,
                            PageRequest.of(0, maxExpansions)
                    );

            for (SkillCooccurrence cooccurrence : cooccurrences) {
                String coSkillName = cooccurrence.getCoSkill().getName();
                if (!containsSkill(keyword, coSkillName)) {
                    expandedSkillNames.add(coSkillName);
                }

                if (expandedSkillNames.size() >= maxExpansions) {
                    break;
                }
            }
        }

        return List.copyOf(expandedSkillNames);
    }

    private List<Skill> findMentionedSkills(String keyword) {
        return skillRepository.findAllByOrderByNameAsc().stream()
                .filter(skill -> containsSkill(keyword, skill.getName())
                        || containsSkill(keyword, skill.getNormalizedName()))
                .toList();
    }

    private boolean containsSkill(String keyword, String skillName) {
        String normalizedKeyword = normalize(keyword);
        String normalizedSkillName = normalize(skillName);
        if (normalizedSkillName.isBlank()) {
            return false;
        }

        Pattern skillPattern = Pattern.compile(
                "(^|[^a-z0-9가-힣+#.])"
                        + Pattern.quote(normalizedSkillName)
                        + "($|[^a-z0-9가-힣+#.])"
        );
        return skillPattern.matcher(normalizedKeyword).find();
    }

    private String normalize(String text) {
        if (text == null) {
            return "";
        }

        return text.toLowerCase(Locale.ROOT)
                .trim()
                .replaceAll("\\s+", " ");
    }
}
