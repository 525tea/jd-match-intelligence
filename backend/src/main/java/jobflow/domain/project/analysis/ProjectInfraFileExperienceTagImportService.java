package jobflow.domain.project.analysis;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import jobflow.domain.project.UserProject;
import jobflow.domain.project.UserProjectAnalysis;
import jobflow.domain.project.UserProjectAnalysisRepository;
import jobflow.domain.project.UserProjectExperienceTag;
import jobflow.domain.project.UserProjectExperienceTagRepository;
import jobflow.domain.project.UserProjectRepository;
import jobflow.domain.skill.ExperienceTagCode;
import jobflow.domain.skill.ExperienceTagCodeRepository;
import jobflow.global.error.ErrorCode;
import jobflow.global.error.exception.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ProjectInfraFileExperienceTagImportService {

    private static final String MODEL_VERSION = "infra-file-static-v1";
    private static final int MAX_EVIDENCE_LENGTH = 500;

    private final UserProjectRepository userProjectRepository;
    private final UserProjectAnalysisRepository userProjectAnalysisRepository;
    private final UserProjectExperienceTagRepository userProjectExperienceTagRepository;
    private final ExperienceTagCodeRepository experienceTagCodeRepository;
    private final ProjectInfraFileAnalysisService projectInfraFileAnalysisService;

    public ProjectInfraFileExperienceTagImportService(
            UserProjectRepository userProjectRepository,
            UserProjectAnalysisRepository userProjectAnalysisRepository,
            UserProjectExperienceTagRepository userProjectExperienceTagRepository,
            ExperienceTagCodeRepository experienceTagCodeRepository,
            ProjectInfraFileAnalysisService projectInfraFileAnalysisService
    ) {
        this.userProjectRepository = userProjectRepository;
        this.userProjectAnalysisRepository = userProjectAnalysisRepository;
        this.userProjectExperienceTagRepository = userProjectExperienceTagRepository;
        this.experienceTagCodeRepository = experienceTagCodeRepository;
        this.projectInfraFileAnalysisService = projectInfraFileAnalysisService;
    }

    public ProjectInfraFileExperienceTagImportResult importInfraFileExperienceTags(
            Long userId,
            Long userProjectId,
            RepositoryRef repositoryRef
    ) {
        if (userId == null || userProjectId == null) {
            throw new EntityNotFoundException(ErrorCode.USER_PROJECT_NOT_FOUND);
        }

        UserProject userProject = userProjectRepository.findByIdAndUserId(userProjectId, userId)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.USER_PROJECT_NOT_FOUND));

        ProjectInfraFileAnalysisResult analysisResult = projectInfraFileAnalysisService.analyze(repositoryRef);
        Map<String, InfraExperienceTagCandidate> candidatesByTagCode =
                candidatesByTagCode(analysisResult.experienceTagCandidates());
        Map<String, ExperienceTagCode> tagCodesByCode = tagCodesByCode(candidatesByTagCode.keySet());

        int nextVersion = userProjectAnalysisRepository.findMaxAnalysisVersionByUserProjectId(userProjectId) + 1;
        UserProjectAnalysis analysis = UserProjectAnalysis.create(
                userProject,
                nextVersion,
                sourceHash(analysisResult),
                repositoryRef.ref(),
                MODEL_VERSION,
                rawAnalysis(analysisResult),
                averageConfidence(candidatesByTagCode),
                LocalDateTime.now()
        );

        UserProjectAnalysis savedAnalysis = userProjectAnalysisRepository.save(analysis);
        List<UserProjectExperienceTag> projectExperienceTags = candidatesByTagCode.values()
                .stream()
                .filter(candidate -> tagCodesByCode.containsKey(candidate.tagCode()))
                .map(candidate -> UserProjectExperienceTag.create(
                        savedAnalysis,
                        tagCodesByCode.get(candidate.tagCode()),
                        candidate.confidence(),
                        truncate(candidate.evidence())
                ))
                .toList();

        userProjectExperienceTagRepository.saveAll(projectExperienceTags);

        List<String> savedTagCodes = projectExperienceTags.stream()
                .map(projectExperienceTag -> projectExperienceTag.getTagCode().getCode())
                .sorted()
                .toList();
        List<String> unmappedTagCodes = candidatesByTagCode.keySet()
                .stream()
                .filter(tagCode -> !tagCodesByCode.containsKey(tagCode))
                .sorted()
                .toList();

        return new ProjectInfraFileExperienceTagImportResult(
                savedAnalysis.getId(),
                nextVersion,
                candidatesByTagCode.size(),
                projectExperienceTags.size(),
                savedTagCodes,
                unmappedTagCodes
        );
    }

    private Map<String, InfraExperienceTagCandidate> candidatesByTagCode(
            List<InfraExperienceTagCandidate> candidates
    ) {
        return candidates.stream()
                .collect(Collectors.toMap(
                        InfraExperienceTagCandidate::tagCode,
                        Function.identity(),
                        this::chooseHigherConfidence,
                        LinkedHashMap::new
                ));
    }

    private Map<String, ExperienceTagCode> tagCodesByCode(Set<String> tagCodes) {
        if (tagCodes.isEmpty()) {
            return Map.of();
        }

        return experienceTagCodeRepository.findAllById(tagCodes)
                .stream()
                .collect(Collectors.toMap(ExperienceTagCode::getCode, Function.identity()));
    }

    private InfraExperienceTagCandidate chooseHigherConfidence(
            InfraExperienceTagCandidate current,
            InfraExperienceTagCandidate candidate
    ) {
        if (candidate.confidence().compareTo(current.confidence()) > 0) {
            return candidate;
        }
        return current;
    }

    private String sourceHash(ProjectInfraFileAnalysisResult analysisResult) {
        String source = analysisResult.repositoryRef().fullName()
                + ":" + analysisResult.repositoryRef().ref()
                + ":" + String.join(",", analysisResult.analyzedPaths())
                + ":" + analysisResult.experienceTagCandidates()
                .stream()
                .sorted(Comparator.comparing(InfraExperienceTagCandidate::tagCode))
                .map(candidate -> candidate.tagCode() + "=" + candidate.evidence())
                .collect(Collectors.joining("|"));

        return sha256(source);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to create source hash", exception);
        }
    }

    private String rawAnalysis(ProjectInfraFileAnalysisResult analysisResult) {
        String paths = analysisResult.analyzedPaths()
                .stream()
                .map(path -> "\"" + escape(path) + "\"")
                .collect(Collectors.joining(","));
        String tags = analysisResult.experienceTagCandidates()
                .stream()
                .map(candidate -> "{\"tagCode\":\"" + escape(candidate.tagCode())
                        + "\",\"confidence\":" + candidate.confidence()
                        + ",\"evidence\":\"" + escape(candidate.evidence()) + "\"}")
                .collect(Collectors.joining(","));

        return "{"
                + "\"repository\":\"" + escape(analysisResult.repositoryRef().fullName()) + "\","
                + "\"ref\":\"" + escape(analysisResult.repositoryRef().ref()) + "\","
                + "\"requestedFileCount\":" + analysisResult.requestedFileCount() + ","
                + "\"foundFileCount\":" + analysisResult.foundFileCount() + ","
                + "\"analyzedPaths\":[" + paths + "],"
                + "\"experienceTagCandidates\":[" + tags + "]"
                + "}";
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private BigDecimal averageConfidence(Map<String, InfraExperienceTagCandidate> candidatesByTagCode) {
        if (candidatesByTagCode.isEmpty()) {
            return BigDecimal.ZERO;
        }

        double average = candidatesByTagCode.values()
                .stream()
                .mapToDouble(candidate -> candidate.confidence().doubleValue())
                .average()
                .orElse(0.0);

        return BigDecimal.valueOf(average);
    }

    private String truncate(String evidence) {
        if (evidence == null || evidence.length() <= MAX_EVIDENCE_LENGTH) {
            return evidence;
        }
        return evidence.substring(0, MAX_EVIDENCE_LENGTH);
    }
}
