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
import jobflow.domain.project.AnalysisSource;
import jobflow.domain.project.UserProject;
import jobflow.domain.project.UserProjectAnalysis;
import jobflow.domain.project.UserProjectAnalysisRepository;
import jobflow.domain.project.UserProjectRepository;
import jobflow.domain.project.UserProjectSkill;
import jobflow.domain.project.UserProjectSkillRepository;
import jobflow.domain.skill.Skill;
import jobflow.domain.skill.SkillRepository;
import jobflow.global.error.ErrorCode;
import jobflow.global.error.exception.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ProjectBuildFileSkillImportService {

    private static final String MODEL_VERSION = "build-file-static-v1";
    private static final int MAX_EVIDENCE_LENGTH = 500;

    private final UserProjectRepository userProjectRepository;
    private final UserProjectAnalysisRepository userProjectAnalysisRepository;
    private final UserProjectSkillRepository userProjectSkillRepository;
    private final SkillRepository skillRepository;
    private final ProjectBuildFileAnalysisService projectBuildFileAnalysisService;

    public ProjectBuildFileSkillImportService(
            UserProjectRepository userProjectRepository,
            UserProjectAnalysisRepository userProjectAnalysisRepository,
            UserProjectSkillRepository userProjectSkillRepository,
            SkillRepository skillRepository,
            ProjectBuildFileAnalysisService projectBuildFileAnalysisService
    ) {
        this.userProjectRepository = userProjectRepository;
        this.userProjectAnalysisRepository = userProjectAnalysisRepository;
        this.userProjectSkillRepository = userProjectSkillRepository;
        this.skillRepository = skillRepository;
        this.projectBuildFileAnalysisService = projectBuildFileAnalysisService;
    }

    public ProjectBuildFileSkillImportResult importBuildFileSkills(
            Long userId,
            Long userProjectId,
            RepositoryRef repositoryRef
    ) {
        if (userId == null || userProjectId == null) {
            throw new EntityNotFoundException(ErrorCode.USER_PROJECT_NOT_FOUND);
        }

        UserProject userProject = userProjectRepository.findByIdAndUserId(userProjectId, userId)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.USER_PROJECT_NOT_FOUND));

        ProjectBuildFileAnalysisResult analysisResult = projectBuildFileAnalysisService.analyze(repositoryRef);
        List<BuildFileSkillCandidate> candidates = analysisResult.skillCandidates();
        Map<String, BuildFileSkillCandidate> candidatesByName = candidatesByName(candidates);
        Map<String, Skill> skillsByName = skillsByName(candidatesByName.keySet());

        int nextVersion = userProjectAnalysisRepository.findMaxAnalysisVersionByUserProjectId(userProjectId) + 1;
        UserProjectAnalysis analysis = UserProjectAnalysis.create(
                userProject,
                nextVersion,
                sourceHash(analysisResult),
                repositoryRef.ref(),
                MODEL_VERSION,
                rawAnalysis(analysisResult),
                averageConfidence(candidatesByName),
                LocalDateTime.now()
        );

        UserProjectAnalysis savedAnalysis = userProjectAnalysisRepository.save(analysis);
        List<UserProjectSkill> projectSkills = candidatesByName.values()
                .stream()
                .filter(candidate -> skillsByName.containsKey(candidate.skillName()))
                .map(candidate -> UserProjectSkill.create(
                        savedAnalysis,
                        skillsByName.get(candidate.skillName()),
                        candidate.confidence(),
                        truncate(candidate.evidence()),
                        AnalysisSource.STATIC
                ))
                .toList();

        userProjectSkillRepository.saveAll(projectSkills);

        List<String> savedSkillNames = projectSkills.stream()
                .map(projectSkill -> projectSkill.getSkill().getName())
                .sorted()
                .toList();
        List<String> unmappedSkillNames = candidatesByName.keySet()
                .stream()
                .filter(skillName -> !skillsByName.containsKey(skillName))
                .sorted()
                .toList();

        return new ProjectBuildFileSkillImportResult(
                savedAnalysis.getId(),
                nextVersion,
                candidatesByName.size(),
                projectSkills.size(),
                savedSkillNames,
                unmappedSkillNames
        );
    }

    private Map<String, BuildFileSkillCandidate> candidatesByName(List<BuildFileSkillCandidate> candidates) {
        return candidates.stream()
                .collect(Collectors.toMap(
                        BuildFileSkillCandidate::skillName,
                        Function.identity(),
                        this::chooseHigherConfidence,
                        LinkedHashMap::new
                ));
    }

    private Map<String, Skill> skillsByName(Set<String> skillNames) {
        if (skillNames.isEmpty()) {
            return Map.of();
        }

        return skillRepository.findByNameIn(skillNames)
                .stream()
                .collect(Collectors.toMap(Skill::getName, Function.identity()));
    }

    private BuildFileSkillCandidate chooseHigherConfidence(
            BuildFileSkillCandidate current,
            BuildFileSkillCandidate candidate
    ) {
        if (candidate.confidence().compareTo(current.confidence()) > 0) {
            return candidate;
        }
        return current;
    }

    private String sourceHash(ProjectBuildFileAnalysisResult analysisResult) {
        String source = analysisResult.repositoryRef().fullName()
                + ":" + analysisResult.repositoryRef().ref()
                + ":" + String.join(",", analysisResult.analyzedPaths())
                + ":" + analysisResult.skillCandidates()
                .stream()
                .sorted(Comparator.comparing(BuildFileSkillCandidate::skillName))
                .map(candidate -> candidate.skillName() + "=" + candidate.evidence())
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

    private String rawAnalysis(ProjectBuildFileAnalysisResult analysisResult) {
        String paths = analysisResult.analyzedPaths()
                .stream()
                .map(path -> "\"" + escape(path) + "\"")
                .collect(Collectors.joining(","));
        String skills = analysisResult.skillCandidates()
                .stream()
                .map(candidate -> "{\"skillName\":\"" + escape(candidate.skillName())
                        + "\",\"confidence\":" + candidate.confidence()
                        + ",\"evidence\":\"" + escape(candidate.evidence()) + "\"}")
                .collect(Collectors.joining(","));

        return "{"
                + "\"repository\":\"" + escape(analysisResult.repositoryRef().fullName()) + "\","
                + "\"ref\":\"" + escape(analysisResult.repositoryRef().ref()) + "\","
                + "\"requestedFileCount\":" + analysisResult.requestedFileCount() + ","
                + "\"foundFileCount\":" + analysisResult.foundFileCount() + ","
                + "\"analyzedPaths\":[" + paths + "],"
                + "\"skillCandidates\":[" + skills + "]"
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

    private BigDecimal averageConfidence(Map<String, BuildFileSkillCandidate> candidatesByName) {
        if (candidatesByName.isEmpty()) {
            return BigDecimal.ZERO;
        }

        double average = candidatesByName.values()
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
