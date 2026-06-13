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
import java.util.stream.Stream;
import jobflow.domain.project.AnalysisSource;
import jobflow.domain.project.UserProject;
import jobflow.domain.project.UserProjectAnalysis;
import jobflow.domain.project.UserProjectAnalysisRepository;
import jobflow.domain.project.UserProjectExperienceTag;
import jobflow.domain.project.UserProjectExperienceTagRepository;
import jobflow.domain.project.UserProjectRepository;
import jobflow.domain.project.UserProjectSkill;
import jobflow.domain.project.UserProjectSkillRepository;
import jobflow.domain.skill.ExperienceTagCode;
import jobflow.domain.skill.ExperienceTagCodeRepository;
import jobflow.domain.skill.Skill;
import jobflow.domain.skill.SkillRepository;
import jobflow.global.error.ErrorCode;
import jobflow.global.error.exception.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ProjectRepositoryStaticAnalysisImportService {

    private static final String MODEL_VERSION = "repository-static-v1";
    private static final int MAX_EVIDENCE_LENGTH = 500;

    private final UserProjectRepository userProjectRepository;
    private final UserProjectAnalysisRepository userProjectAnalysisRepository;
    private final UserProjectSkillRepository userProjectSkillRepository;
    private final UserProjectExperienceTagRepository userProjectExperienceTagRepository;
    private final SkillRepository skillRepository;
    private final ExperienceTagCodeRepository experienceTagCodeRepository;
    private final ProjectBuildFileAnalysisService projectBuildFileAnalysisService;
    private final ProjectInfraFileAnalysisService projectInfraFileAnalysisService;
    private final ProjectWorkflowFileAnalysisService projectWorkflowFileAnalysisService;

    public ProjectRepositoryStaticAnalysisImportService(
            UserProjectRepository userProjectRepository,
            UserProjectAnalysisRepository userProjectAnalysisRepository,
            UserProjectSkillRepository userProjectSkillRepository,
            UserProjectExperienceTagRepository userProjectExperienceTagRepository,
            SkillRepository skillRepository,
            ExperienceTagCodeRepository experienceTagCodeRepository,
            ProjectBuildFileAnalysisService projectBuildFileAnalysisService,
            ProjectInfraFileAnalysisService projectInfraFileAnalysisService,
            ProjectWorkflowFileAnalysisService projectWorkflowFileAnalysisService
    ) {
        this.userProjectRepository = userProjectRepository;
        this.userProjectAnalysisRepository = userProjectAnalysisRepository;
        this.userProjectSkillRepository = userProjectSkillRepository;
        this.userProjectExperienceTagRepository = userProjectExperienceTagRepository;
        this.skillRepository = skillRepository;
        this.experienceTagCodeRepository = experienceTagCodeRepository;
        this.projectBuildFileAnalysisService = projectBuildFileAnalysisService;
        this.projectInfraFileAnalysisService = projectInfraFileAnalysisService;
        this.projectWorkflowFileAnalysisService = projectWorkflowFileAnalysisService;
    }

    public ProjectRepositoryStaticAnalysisImportResult importRepositoryStaticAnalysis(
            Long userId,
            Long userProjectId,
            RepositoryRef repositoryRef
    ) {
        if (userId == null || userProjectId == null) {
            throw new EntityNotFoundException(ErrorCode.USER_PROJECT_NOT_FOUND);
        }

        UserProject userProject = userProjectRepository.findByIdAndUserId(userProjectId, userId)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.USER_PROJECT_NOT_FOUND));

        ProjectBuildFileAnalysisResult buildFileAnalysis = projectBuildFileAnalysisService.analyze(repositoryRef);
        ProjectInfraFileAnalysisResult infraFileAnalysis = projectInfraFileAnalysisService.analyze(repositoryRef);
        ProjectWorkflowFileAnalysisResult workflowFileAnalysis = projectWorkflowFileAnalysisService.analyze(repositoryRef);

        Map<String, BuildFileSkillCandidate> skillCandidatesByName =
                skillCandidatesByName(buildFileAnalysis.skillCandidates());
        Map<String, InfraExperienceTagCandidate> tagCandidatesByCode =
                tagCandidatesByCode(infraFileAnalysis.experienceTagCandidates(), workflowFileAnalysis.experienceTagCandidates());
        Map<String, Skill> skillsByName = skillsByName(skillCandidatesByName.keySet());
        Map<String, ExperienceTagCode> tagCodesByCode = tagCodesByCode(tagCandidatesByCode.keySet());

        int nextVersion = userProjectAnalysisRepository.findMaxAnalysisVersionByUserProjectId(userProjectId) + 1;
        UserProjectAnalysis analysis = UserProjectAnalysis.create(
                userProject,
                nextVersion,
                sourceHash(buildFileAnalysis, infraFileAnalysis, workflowFileAnalysis),
                repositoryRef.ref(),
                MODEL_VERSION,
                rawAnalysis(buildFileAnalysis, infraFileAnalysis, workflowFileAnalysis),
                averageConfidence(skillCandidatesByName, tagCandidatesByCode),
                LocalDateTime.now()
        );

        UserProjectAnalysis savedAnalysis = userProjectAnalysisRepository.save(analysis);
        List<UserProjectSkill> projectSkills = skillCandidatesByName.values()
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
        List<UserProjectExperienceTag> projectExperienceTags = tagCandidatesByCode.values()
                .stream()
                .filter(candidate -> tagCodesByCode.containsKey(candidate.tagCode()))
                .map(candidate -> UserProjectExperienceTag.create(
                        savedAnalysis,
                        tagCodesByCode.get(candidate.tagCode()),
                        candidate.confidence(),
                        truncate(candidate.evidence())
                ))
                .toList();

        userProjectSkillRepository.saveAll(projectSkills);
        userProjectExperienceTagRepository.saveAll(projectExperienceTags);

        return new ProjectRepositoryStaticAnalysisImportResult(
                savedAnalysis.getId(),
                nextVersion,
                skillCandidatesByName.size(),
                projectSkills.size(),
                savedSkillNames(projectSkills),
                unmappedSkillNames(skillCandidatesByName.keySet(), skillsByName),
                tagCandidatesByCode.size(),
                projectExperienceTags.size(),
                savedTagCodes(projectExperienceTags),
                unmappedTagCodes(tagCandidatesByCode.keySet(), tagCodesByCode)
        );
    }

    private Map<String, BuildFileSkillCandidate> skillCandidatesByName(
            List<BuildFileSkillCandidate> candidates
    ) {
        return candidates.stream()
                .collect(Collectors.toMap(
                        BuildFileSkillCandidate::skillName,
                        Function.identity(),
                        this::chooseHigherConfidence,
                        LinkedHashMap::new
                ));
    }

    private Map<String, InfraExperienceTagCandidate> tagCandidatesByCode(
            List<InfraExperienceTagCandidate> infraCandidates,
            List<WorkflowExperienceTagCandidate> workflowCandidates
    ) {
        return Stream.concat(
                        infraCandidates.stream(),
                        workflowCandidates.stream()
                                .map(candidate -> InfraExperienceTagCandidate.of(
                                        candidate.tagCode(),
                                        candidate.confidence().doubleValue(),
                                        candidate.evidence()
                                ))
                )
                .collect(Collectors.toMap(
                        InfraExperienceTagCandidate::tagCode,
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

    private Map<String, ExperienceTagCode> tagCodesByCode(Set<String> tagCodes) {
        if (tagCodes.isEmpty()) {
            return Map.of();
        }

        return experienceTagCodeRepository.findAllById(tagCodes)
                .stream()
                .collect(Collectors.toMap(ExperienceTagCode::getCode, Function.identity()));
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

    private InfraExperienceTagCandidate chooseHigherConfidence(
            InfraExperienceTagCandidate current,
            InfraExperienceTagCandidate candidate
    ) {
        if (candidate.confidence().compareTo(current.confidence()) > 0) {
            return candidate;
        }
        return current;
    }

    private String sourceHash(
            ProjectBuildFileAnalysisResult buildFileAnalysis,
            ProjectInfraFileAnalysisResult infraFileAnalysis,
            ProjectWorkflowFileAnalysisResult workflowFileAnalysis
    ) {
        String source = buildFileAnalysis.repositoryRef().fullName()
                + ":" + buildFileAnalysis.repositoryRef().ref()
                + ":build=" + String.join(",", buildFileAnalysis.analyzedPaths())
                + ":infra=" + String.join(",", infraFileAnalysis.analyzedPaths())
                + ":workflow=" + String.join(",", workflowFileAnalysis.analyzedPaths())
                + ":skills=" + buildFileAnalysis.skillCandidates()
                .stream()
                .sorted(Comparator.comparing(BuildFileSkillCandidate::skillName))
                .map(candidate -> candidate.skillName() + "=" + candidate.evidence())
                .collect(Collectors.joining("|"))
                + ":infraTags=" + infraFileAnalysis.experienceTagCandidates()
                .stream()
                .sorted(Comparator.comparing(InfraExperienceTagCandidate::tagCode))
                .map(candidate -> candidate.tagCode() + "=" + candidate.evidence())
                .collect(Collectors.joining("|"))
                + ":workflowTags=" + workflowFileAnalysis.experienceTagCandidates()
                .stream()
                .sorted(Comparator.comparing(WorkflowExperienceTagCandidate::tagCode))
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

    private String rawAnalysis(
            ProjectBuildFileAnalysisResult buildFileAnalysis,
            ProjectInfraFileAnalysisResult infraFileAnalysis,
            ProjectWorkflowFileAnalysisResult workflowFileAnalysis
    ) {
        String buildPaths = toJsonStringArray(buildFileAnalysis.analyzedPaths());
        String infraPaths = toJsonStringArray(infraFileAnalysis.analyzedPaths());
        String workflowPaths = toJsonStringArray(workflowFileAnalysis.analyzedPaths());
        String skills = buildFileAnalysis.skillCandidates()
                .stream()
                .map(candidate -> "{\"skillName\":\"" + escape(candidate.skillName())
                        + "\",\"confidence\":" + candidate.confidence()
                        + ",\"evidence\":\"" + escape(candidate.evidence()) + "\"}")
                .collect(Collectors.joining(","));
        String tags = infraFileAnalysis.experienceTagCandidates()
                .stream()
                .map(candidate -> "{\"tagCode\":\"" + escape(candidate.tagCode())
                        + "\",\"confidence\":" + candidate.confidence()
                        + ",\"evidence\":\"" + escape(candidate.evidence()) + "\"}")
                .collect(Collectors.joining(","));
        String workflowTags = workflowFileAnalysis.experienceTagCandidates()
                .stream()
                .map(candidate -> "{\"tagCode\":\"" + escape(candidate.tagCode())
                        + "\",\"confidence\":" + candidate.confidence()
                        + ",\"evidence\":\"" + escape(candidate.evidence()) + "\"}")
                .collect(Collectors.joining(","));

        return "{"
                + "\"repository\":\"" + escape(buildFileAnalysis.repositoryRef().fullName()) + "\","
                + "\"ref\":\"" + escape(buildFileAnalysis.repositoryRef().ref()) + "\","
                + "\"buildFileAnalysis\":{"
                + "\"requestedFileCount\":" + buildFileAnalysis.requestedFileCount() + ","
                + "\"foundFileCount\":" + buildFileAnalysis.foundFileCount() + ","
                + "\"analyzedPaths\":[" + buildPaths + "],"
                + "\"skillCandidates\":[" + skills + "]"
                + "},"
                + "\"infraFileAnalysis\":{"
                + "\"requestedFileCount\":" + infraFileAnalysis.requestedFileCount() + ","
                + "\"foundFileCount\":" + infraFileAnalysis.foundFileCount() + ","
                + "\"analyzedPaths\":[" + infraPaths + "],"
                + "\"experienceTagCandidates\":[" + tags + "]"
                + "},"
                + "\"workflowFileAnalysis\":{"
                + "\"requestedFileCount\":" + workflowFileAnalysis.requestedFileCount() + ","
                + "\"foundFileCount\":" + workflowFileAnalysis.foundFileCount() + ","
                + "\"analyzedPaths\":[" + workflowPaths + "],"
                + "\"experienceTagCandidates\":[" + workflowTags + "]"
                + "}"
                + "}";
    }

    private String toJsonStringArray(List<String> values) {
        return values.stream()
                .map(value -> "\"" + escape(value) + "\"")
                .collect(Collectors.joining(","));
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

    private BigDecimal averageConfidence(
            Map<String, BuildFileSkillCandidate> skillCandidatesByName,
            Map<String, InfraExperienceTagCandidate> tagCandidatesByCode
    ) {
        List<BigDecimal> confidences = Stream.concat(
                        skillCandidatesByName.values().stream().map(BuildFileSkillCandidate::confidence),
                        tagCandidatesByCode.values().stream().map(InfraExperienceTagCandidate::confidence)
                )
                .toList();
        if (confidences.isEmpty()) {
            return BigDecimal.ZERO;
        }

        double average = confidences.stream()
                .mapToDouble(BigDecimal::doubleValue)
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

    private List<String> savedSkillNames(List<UserProjectSkill> projectSkills) {
        return projectSkills.stream()
                .map(projectSkill -> projectSkill.getSkill().getName())
                .sorted()
                .toList();
    }

    private List<String> unmappedSkillNames(
            Set<String> candidateSkillNames,
            Map<String, Skill> skillsByName
    ) {
        return candidateSkillNames.stream()
                .filter(skillName -> !skillsByName.containsKey(skillName))
                .sorted()
                .toList();
    }

    private List<String> savedTagCodes(List<UserProjectExperienceTag> projectExperienceTags) {
        return projectExperienceTags.stream()
                .map(projectExperienceTag -> projectExperienceTag.getTagCode().getCode())
                .sorted()
                .toList();
    }

    private List<String> unmappedTagCodes(
            Set<String> candidateTagCodes,
            Map<String, ExperienceTagCode> tagCodesByCode
    ) {
        return candidateTagCodes.stream()
                .filter(tagCode -> !tagCodesByCode.containsKey(tagCode))
                .sorted()
                .toList();
    }
}
