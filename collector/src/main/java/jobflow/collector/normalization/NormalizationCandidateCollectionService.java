package jobflow.collector.normalization;

import static java.util.stream.Collectors.toCollection;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jobflow.collector.job.Job;
import jobflow.collector.job.JobRepository;
import jobflow.collector.skill.SkillAlias;
import jobflow.collector.skill.SkillAliasRepository;
import jobflow.collector.skill.SkillRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NormalizationCandidateCollectionService {

    private static final Pattern BRACKET_LABEL_PATTERN = Pattern.compile("^\\s*\\[([^\\]\\n]{2,60})]\\s*$");
    private static final Pattern SKILL_HEADER_PATTERN = Pattern.compile(
            "^(?:\\[\\s*)?(기술\\s*스택|사용\\s*기술|보유\\s*기술|tech\\s*stack|skills?)(?:\\s*])?$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern SECTION_LIKE_LINE_PATTERN = Pattern.compile(
            "^(?:\\[\\s*)?([가-힣A-Za-z0-9 /&·+().-]{2,60})(?:\\s*])?$"
    );
    private static final Pattern TECH_TOKEN_PATTERN = Pattern.compile(
            "[A-Za-z][A-Za-z0-9+#.\\-]{1,39}|[가-힣A-Za-z0-9+#.\\-]{2,40}"
    );

    private static final Set<String> KNOWN_SECTION_LABELS = Set.of(
            "회사 소개",
            "회사소개",
            "기업 서비스 소개",
            "팀 소개",
            "포지션 상세 정보",
            "포지션 경력/학력/마감일/근무지역 정보",
            "기술스택",
            "기술 스택",
            "기술 스택&사용 툴",
            "사용기술",
            "사용 기술 및 환경",
            "주요 업무",
            "주요업무",
            "담당 업무",
            "자격 요건",
            "자격요건",
            "지원 자격",
            "필수 요건",
            "우대 사항",
            "우대사항",
            "우대 조건",
            "혜택 및 복지",
            "복지 및 혜택",
            "복지",
            "채용 절차",
            "채용절차",
            "채용절차 및 기타 지원 유의사항",
            "채용 전형",
            "전형절차",
            "지원 시 주의사항",
            "지원 시 유의사항",
            "지원 시 꼭 확인해주세요",
            "지원시 안내",
            "지원 서류",
            "공통자격",
            "근무정보",
            "기술",
            "활용 기술",
            "활용 기술 (Skill Set)",
            "이력서 및 경력기술서",
            "근무조건",
            "근무형태",
            "근무 지역",
            "requirements",
            "requirement",
            "qualifications",
            "responsibilities",
            "preferred",
            "nice to have",
            "benefits"
    );

    private static final Set<String> SECTION_KEYWORDS = Set.of(
            "기술",
            "스택",
            "업무",
            "담당",
            "자격",
            "요건",
            "우대",
            "복지",
            "혜택",
            "채용",
            "전형",
            "절차",
            "프로세스",
            "단계",
            "소개",
            "팀",
            "근무",
            "환경",
            "responsibilities",
            "requirements",
            "qualifications",
            "preferred",
            "benefits",
            "process"
    );

    private static final Set<String> TECH_STOP_WORDS = Set.of(
            "and",
            "or",
            "with",
            "the",
            "for",
            "to",
            "in",
            "of",
            "api",
            "apis",
            "rest",
            "기반",
            "경험",
            "개발",
            "운영",
            "설계",
            "사용",
            "기술",
            "스택",
            "이상",
            "우대",
            "필수",
            "boot",
            "framework"
    );

    private final JobRepository jobRepository;
    private final SkillRepository skillRepository;
    private final SkillAliasRepository skillAliasRepository;
    private final NormalizationCandidateRepository candidateRepository;

    @Transactional
    public NormalizationCandidateCollectionSummary collect(List<String> sources) {
        List<Job> jobs = jobRepository.findBySourceInOrderByIdAsc(sources);
        Set<String> knownSkillAliases = knownSkillAliases();
        Set<String> knownSectionLabels = normalizedSet(KNOWN_SECTION_LABELS);

        int skillAliasCandidateCount = 0;
        int sectionLabelCandidateCount = 0;

        for (Job job : jobs) {
            CandidateScanResult result = scan(job, knownSkillAliases, knownSectionLabels);

            for (CandidateValue candidate : result.skillAliasCandidates()) {
                upsert(NormalizationCandidateType.SKILL_ALIAS, job, candidate);
                skillAliasCandidateCount++;
            }

            for (CandidateValue candidate : result.sectionLabelCandidates()) {
                upsert(NormalizationCandidateType.JD_SECTION_LABEL, job, candidate);
                sectionLabelCandidateCount++;
            }
        }

        return new NormalizationCandidateCollectionSummary(
                jobs.size(),
                skillAliasCandidateCount,
                sectionLabelCandidateCount
        );
    }

    private CandidateScanResult scan(
            Job job,
            Set<String> knownSkillAliases,
            Set<String> knownSectionLabels
    ) {
        String description = normalizeLineEndings(job.getDescription());

        if (description.isBlank()) {
            return CandidateScanResult.empty();
        }

        List<CandidateValue> skillAliasCandidates = collectSkillAliasCandidates(description, knownSkillAliases);
        List<CandidateValue> sectionLabelCandidates = collectSectionLabelCandidates(description, knownSectionLabels);

        return new CandidateScanResult(skillAliasCandidates, sectionLabelCandidates);
    }

    private List<CandidateValue> collectSkillAliasCandidates(
            String description,
            Set<String> knownSkillAliases
    ) {
        List<String> skillBlocks = skillBlocks(description);
        Map<String, CandidateValue> candidatesByNormalizedValue = new LinkedHashMap<>();

        for (String block : skillBlocks) {
            Matcher matcher = TECH_TOKEN_PATTERN.matcher(block);

            while (matcher.find()) {
                String value = matcher.group().trim();
                String normalizedValue = normalize(value);

                if (isValidSkillAliasCandidate(value, normalizedValue, knownSkillAliases)) {
                    candidatesByNormalizedValue.putIfAbsent(
                            normalizedValue,
                            new CandidateValue(value, normalizedValue, sampleContext(block, value))
                    );
                }
            }
        }

        return List.copyOf(candidatesByNormalizedValue.values());
    }

    private List<String> skillBlocks(String description) {
        String[] lines = description.split("\\n");
        List<String> blocks = new ArrayList<>();
        StringBuilder currentBlock = null;

        for (String line : lines) {
            String trimmed = line.trim();

            if (isSkillHeader(trimmed)) {
                if (currentBlock != null && !currentBlock.isEmpty()) {
                    blocks.add(currentBlock.toString().trim());
                }

                currentBlock = new StringBuilder();
                continue;
            }

            if (currentBlock == null) {
                continue;
            }

            if (isSectionLikeLine(trimmed) && currentBlock.length() > 0) {
                blocks.add(currentBlock.toString().trim());
                currentBlock = null;
                continue;
            }

            if (!trimmed.isBlank()) {
                currentBlock.append(trimmed).append('\n');
            }
        }

        if (currentBlock != null && !currentBlock.isEmpty()) {
            blocks.add(currentBlock.toString().trim());
        }

        return blocks;
    }

    private List<CandidateValue> collectSectionLabelCandidates(
            String description,
            Set<String> knownSectionLabels
    ) {
        Map<String, CandidateValue> candidatesByNormalizedValue = new LinkedHashMap<>();

        for (String line : description.split("\\n")) {
            String trimmed = line.trim();

            if (!isSectionLikeLine(trimmed)) {
                continue;
            }

            String label = sectionLabelValue(trimmed);
            String normalizedLabel = normalize(label);

            if (isValidSectionLabelCandidate(label, normalizedLabel, knownSectionLabels)) {
                candidatesByNormalizedValue.putIfAbsent(
                        normalizedLabel,
                        new CandidateValue(label, normalizedLabel, trimmed)
                );
            }
        }

        return List.copyOf(candidatesByNormalizedValue.values());
    }

    private void upsert(
            NormalizationCandidateType type,
            Job job,
            CandidateValue candidate
    ) {
        NormalizationCandidate normalizationCandidate = candidateRepository
                .findByTypeAndSourceAndNormalizedValue(type, job.getSource(), candidate.normalizedValue())
                .orElseGet(() -> NormalizationCandidate.firstSeen(
                        type,
                        job.getSource(),
                        candidate.value(),
                        candidate.normalizedValue(),
                        job.getId(),
                        job.getTitle(),
                        candidate.sampleContext()
                ));

        if (normalizationCandidate.getId() != null) {
            normalizationCandidate.recordOccurrence(
                    job.getId(),
                    candidate.value(),
                    job.getTitle(),
                    candidate.sampleContext()
            );
        }

        candidateRepository.save(normalizationCandidate);
    }

    private Set<String> knownSkillAliases() {
        LinkedHashSet<String> aliases = skillRepository.findAllByOrderByNameAsc()
                .stream()
                .map(skill -> normalize(skill.getName()))
                .collect(toCollection(LinkedHashSet::new));

        skillRepository.findAllByOrderByNameAsc()
                .stream()
                .map(skill -> normalize(skill.getNormalizedName()))
                .forEach(aliases::add);

        skillAliasRepository.findByEnabledTrueOrderByNormalizedAliasAsc()
                .stream()
                .map(SkillAlias::getAlias)
                .map(this::normalize)
                .forEach(aliases::add);

        skillAliasRepository.findByEnabledTrueOrderByNormalizedAliasAsc()
                .stream()
                .map(SkillAlias::getNormalizedAlias)
                .map(this::normalize)
                .forEach(aliases::add);

        return aliases;
    }

    private boolean isValidSkillAliasCandidate(
            String value,
            String normalizedValue,
            Set<String> knownSkillAliases
    ) {
        return !normalizedValue.isBlank()
                && normalizedValue.length() >= 2
                && normalizedValue.length() <= 60
                && !knownSkillAliases.contains(normalizedValue)
                && !TECH_STOP_WORDS.contains(normalizedValue)
                && containsLetter(value);
    }

    private boolean isValidSectionLabelCandidate(
            String value,
            String normalizedValue,
            Set<String> knownSectionLabels
    ) {
        return !normalizedValue.isBlank()
                && normalizedValue.length() >= 2
                && normalizedValue.length() <= 60
                && !knownSectionLabels.contains(normalizedValue)
                && !knownSectionLabels.contains(removeSpaces(normalizedValue))
                && containsAnySectionKeyword(normalizedValue)
                && !looksLikeNumberedListItem(value)
                && !looksLikeBenefitLabel(normalizedValue)
                && !looksLikeLegalOrProcessSentence(normalizedValue)
                && !looksLikeSentence(value);
    }

    private boolean isSkillHeader(String line) {
        return SKILL_HEADER_PATTERN.matcher(line).matches();
    }

    private boolean isSectionLikeLine(String line) {
        if (line.isBlank() || line.length() > 80) {
            return false;
        }

        if (isBulletLine(line)) {
            return false;
        }

        if (BRACKET_LABEL_PATTERN.matcher(line).matches()) {
            return true;
        }

        return SECTION_LIKE_LINE_PATTERN.matcher(line).matches()
                && containsAnySectionKeyword(normalize(line));
    }

    private String sectionLabelValue(String line) {
        Matcher bracketMatcher = BRACKET_LABEL_PATTERN.matcher(line);

        if (bracketMatcher.matches()) {
            return bracketMatcher.group(1).trim();
        }

        Matcher lineMatcher = SECTION_LIKE_LINE_PATTERN.matcher(line);

        if (lineMatcher.matches()) {
            return lineMatcher.group(1).trim();
        }

        return line.trim();
    }

    private boolean containsAnySectionKeyword(String normalizedValue) {
        return SECTION_KEYWORDS.stream().anyMatch(normalizedValue::contains);
    }

    private boolean looksLikeSentence(String value) {
        String normalizedValue = value.trim();
        return normalizedValue.contains("합니다")
                || normalizedValue.contains("입니다")
                || normalizedValue.contains("있습니다")
                || normalizedValue.contains("해요")
                || normalizedValue.contains("드려요")
                || normalizedValue.contains("경험이")
                || normalizedValue.contains("뛰어나신")
                || normalizedValue.contains("소유자")
                || normalizedValue.contains("진행")
                || normalizedValue.contains("가능")
                || normalizedValue.contains("바탕으로")
                || normalizedValue.contains("위한")
                || normalizedValue.contains("위해")
                || normalizedValue.contains("있도록")
                || normalizedValue.contains("대한")
                || normalizedValue.contains("존재하는")
                || normalizedValue.contains("없애는")
                || normalizedValue.contains("아름다움")
                || normalizedValue.contains("기다립니다")
                || normalizedValue.contains("만들어갈")
                || normalizedValue.contains("책임은")
                || normalizedValue.contains("효율성")
                || normalizedValue.contains("능률")
                || normalizedValue.contains("유지보수")
                || normalizedValue.contains("그 결과")
                || normalizedValue.contains("경우")
                || normalizedValue.contains("취소")
                || normalizedValue.contains("분을")
                || normalizedValue.endsWith(".");
    }

    private boolean looksLikeBenefitLabel(String normalizedValue) {
        return normalizedValue.contains("경조사")
                || normalizedValue.contains("동호회")
                || normalizedValue.contains("셔틀버스")
                || normalizedValue.contains("자녀")
                || normalizedValue.contains("학비")
                || normalizedValue.contains("통신비")
                || normalizedValue.contains("건강검진")
                || normalizedValue.contains("학술대회")
                || normalizedValue.contains("전시회")
                || normalizedValue.contains("work life")
                || normalizedValue.contains("밸런스");
    }

    private boolean looksLikeLegalOrProcessSentence(String normalizedValue) {
        return normalizedValue.contains("채용절차의 공정화")
                || normalizedValue.contains("전형과정")
                || normalizedValue.contains("서류 전형")
                || normalizedValue.contains("최종 합격")
                || normalizedValue.contains("처우 협의")
                || normalizedValue.contains("면접");
    }

    private boolean looksLikeNumberedListItem(String value) {
        return value.matches("^\\d+[.)]\\s+.+")
                || value.matches("^경력\\s+.+학력\\s+.+마감일\\s+.+근무지역\\s+.+");
    }

    private boolean isBulletLine(String value) {
        return value.startsWith("-")
                || value.startsWith("*")
                || value.startsWith("•")
                || value.startsWith("ㆍ")
                || value.startsWith("·");
    }

    private boolean containsLetter(String value) {
        return value.chars().anyMatch(Character::isLetter);
    }

    private Set<String> normalizedSet(Collection<String> values) {
        LinkedHashSet<String> normalizedValues = new LinkedHashSet<>();

        for (String value : values) {
            String normalizedValue = normalize(value);
            normalizedValues.add(normalizedValue);
            normalizedValues.add(removeSpaces(normalizedValue));
        }

        return normalizedValues;
    }

    private String removeSpaces(String value) {
        return value.replace(" ", "");
    }

    private String normalizeLineEndings(String value) {
        if (value == null) {
            return "";
        }

        return value.replace("\r\n", "\n")
                .replace('\r', '\n')
                .trim();
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }

        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\p{Punct}&&[^+#.]]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String sampleContext(String block, String value) {
        int index = block.indexOf(value);

        if (index < 0) {
            return block.length() <= 200 ? block : block.substring(0, 200);
        }

        int start = Math.max(0, index - 80);
        int end = Math.min(block.length(), index + value.length() + 80);
        return block.substring(start, end).trim();
    }

    private record CandidateScanResult(
            List<CandidateValue> skillAliasCandidates,
            List<CandidateValue> sectionLabelCandidates
    ) {
        private static CandidateScanResult empty() {
            return new CandidateScanResult(List.of(), List.of());
        }
    }

    private record CandidateValue(
            String value,
            String normalizedValue,
            String sampleContext
    ) {
    }
}
