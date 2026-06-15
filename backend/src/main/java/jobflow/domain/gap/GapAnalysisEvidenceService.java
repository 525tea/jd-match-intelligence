package jobflow.domain.gap;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import jobflow.domain.analytics.AnalyticsPeriodType;
import jobflow.domain.analytics.SkillCooccurrenceRepository;
import jobflow.domain.analytics.SkillExperienceMarketRepository;
import jobflow.domain.analytics.SkillTrend;
import jobflow.domain.analytics.SkillTrendRepository;
import jobflow.domain.analytics.dto.JobSkillMatchResponse;
import jobflow.domain.gap.dto.GapJobMatchEvidenceResponse;
import jobflow.domain.gap.dto.GapLearningConnectionResponse;
import jobflow.domain.gap.dto.GapRelatedTagEvidenceResponse;
import jobflow.domain.gap.dto.GapSkillCooccurrenceEvidenceResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GapAnalysisEvidenceService {

    private static final AnalyticsPeriodType PERIOD_TYPE = AnalyticsPeriodType.MONTHLY;
    private static final long MIN_SUPPORT_COUNT = 3;
    private static final int MAX_EVIDENCE_ITEMS = 5;

    private final SkillTrendRepository skillTrendRepository;
    private final SkillCooccurrenceRepository skillCooccurrenceRepository;
    private final SkillExperienceMarketRepository skillExperienceMarketRepository;

    public GapAnalysisEvidenceService(
            SkillTrendRepository skillTrendRepository,
            SkillCooccurrenceRepository skillCooccurrenceRepository,
            SkillExperienceMarketRepository skillExperienceMarketRepository
    ) {
        this.skillTrendRepository = skillTrendRepository;
        this.skillCooccurrenceRepository = skillCooccurrenceRepository;
        this.skillExperienceMarketRepository = skillExperienceMarketRepository;
    }

    public GapJobMatchEvidenceResponse buildEvidence(JobSkillMatchResponse matchResponse) {
        List<String> missingSkillNames = missingSkillNames(matchResponse);
        if (missingSkillNames.isEmpty()) {
            return GapJobMatchEvidenceResponse.empty();
        }

        long addedJobs = findAddedJobs(missingSkillNames);
        List<GapSkillCooccurrenceEvidenceResponse> cooccurrences = findCooccurrences(missingSkillNames);
        List<GapRelatedTagEvidenceResponse> relatedTags = findRelatedTags(missingSkillNames);
        List<GapLearningConnectionResponse> learningConnections = buildLearningConnections(
                missingSkillNames,
                cooccurrences,
                relatedTags
        );

        return new GapJobMatchEvidenceResponse(
                addedJobs,
                cooccurrences,
                relatedTags,
                learningConnections
        );
    }

    private List<String> missingSkillNames(JobSkillMatchResponse matchResponse) {
        return Stream.concat(
                        matchResponse.missingRequiredSkills().stream(),
                        matchResponse.missingPreferredSkills().stream()
                )
                .filter(skillName -> skillName != null && !skillName.isBlank())
                .distinct()
                .toList();
    }

    private long findAddedJobs(List<String> missingSkillNames) {
        Optional<LocalDate> latestPeriodStart = skillTrendRepository.findLatestPeriodStartByPeriodType(PERIOD_TYPE);
        if (latestPeriodStart.isEmpty()) {
            return 0;
        }

        return skillTrendRepository.findByPeriodTypeAndPeriodStartAndSkillNameIn(
                        PERIOD_TYPE,
                        latestPeriodStart.get(),
                        missingSkillNames
                )
                .stream()
                .mapToLong(SkillTrend::getJobCount)
                .sum();
    }

    private List<GapSkillCooccurrenceEvidenceResponse> findCooccurrences(List<String> missingSkillNames) {
        Optional<LocalDate> latestPeriodStart =
                skillCooccurrenceRepository.findLatestPeriodStartByPeriodType(PERIOD_TYPE);
        if (latestPeriodStart.isEmpty()) {
            return List.of();
        }

        return skillCooccurrenceRepository.findSupportedCooccurrencesByBaseSkillNameIn(
                        PERIOD_TYPE,
                        latestPeriodStart.get(),
                        missingSkillNames,
                        MIN_SUPPORT_COUNT,
                        PageRequest.of(0, MAX_EVIDENCE_ITEMS)
                )
                .stream()
                .map(GapSkillCooccurrenceEvidenceResponse::from)
                .toList();
    }

    private List<GapRelatedTagEvidenceResponse> findRelatedTags(List<String> missingSkillNames) {
        Optional<LocalDate> latestPeriodStart =
                skillExperienceMarketRepository.findLatestPeriodStartByPeriodType(PERIOD_TYPE);
        if (latestPeriodStart.isEmpty()) {
            return List.of();
        }

        return skillExperienceMarketRepository.findSupportedMarketsBySkillNameIn(
                        PERIOD_TYPE,
                        latestPeriodStart.get(),
                        missingSkillNames,
                        MIN_SUPPORT_COUNT,
                        PageRequest.of(0, MAX_EVIDENCE_ITEMS)
                )
                .stream()
                .map(GapRelatedTagEvidenceResponse::from)
                .toList();
    }

    private List<GapLearningConnectionResponse> buildLearningConnections(
            List<String> missingSkillNames,
            List<GapSkillCooccurrenceEvidenceResponse> cooccurrences,
            List<GapRelatedTagEvidenceResponse> relatedTags
    ) {
        Map<String, GapSkillCooccurrenceEvidenceResponse> cooccurrenceBySkillName = new LinkedHashMap<>();
        for (GapSkillCooccurrenceEvidenceResponse cooccurrence : cooccurrences) {
            cooccurrenceBySkillName.putIfAbsent(cooccurrence.baseSkillName(), cooccurrence);
        }

        Map<String, GapRelatedTagEvidenceResponse> relatedTagBySkillName = new LinkedHashMap<>();
        for (GapRelatedTagEvidenceResponse relatedTag : relatedTags) {
            relatedTagBySkillName.putIfAbsent(relatedTag.skillName(), relatedTag);
        }

        return missingSkillNames.stream()
                .map(skillName -> learningConnection(
                        skillName,
                        cooccurrenceBySkillName.get(skillName),
                        relatedTagBySkillName.get(skillName)
                ))
                .toList();
    }

    private GapLearningConnectionResponse learningConnection(
            String missingSkillName,
            GapSkillCooccurrenceEvidenceResponse cooccurrence,
            GapRelatedTagEvidenceResponse relatedTag
    ) {
        if (cooccurrence != null && relatedTag != null) {
            return new GapLearningConnectionResponse(
                    missingSkillName,
                    "%s은(는) %s와 함께 자주 등장하고, %s 경험과 연결됩니다.".formatted(
                            missingSkillName,
                            cooccurrence.relatedSkillName(),
                            relatedTag.tagName()
                    )
            );
        }

        if (cooccurrence != null) {
            return new GapLearningConnectionResponse(
                    missingSkillName,
                    "%s은(는) %s와 함께 자주 등장하는 스킬입니다.".formatted(
                            missingSkillName,
                            cooccurrence.relatedSkillName()
                    )
            );
        }

        if (relatedTag != null) {
            return new GapLearningConnectionResponse(
                    missingSkillName,
                    "%s은(는) %s 경험과 연결되는 스킬입니다.".formatted(
                            missingSkillName,
                            relatedTag.tagName()
                    )
            );
        }

        return new GapLearningConnectionResponse(
                missingSkillName,
                "%s은(는) 현재 매칭 공고에서 부족한 스킬입니다.".formatted(missingSkillName)
        );
    }
}
