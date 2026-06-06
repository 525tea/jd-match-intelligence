package jobflow.domain.job;

import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class JdJobRoleClassificationService {

    private static final Pattern NON_WORD_PATTERN = Pattern.compile("[^a-z0-9가-힣+#.]+");

    public JobRole resolve(JobRole providedRole, String... texts) {
        if (providedRole != null && providedRole != JobRole.ETC) {
            return providedRole;
        }

        return classify(texts);
    }

    public JobRole classify(String... texts) {
        String text = normalize(String.join(" ", nonNullTexts(texts)));

        if (matchesAny(text, "fullstack", "full stack", "full-stack", "풀스택")) {
            return JobRole.FULLSTACK;
        }

        if (matchesAny(text, "android", "안드로이드")) {
            return JobRole.ANDROID;
        }

        if (matchesAny(text, "ios", "iphone", "swift", "objective c", "아이폰", "iOS")) {
            return JobRole.IOS;
        }

        if (matchesAny(text, "machine learning", "ml engineer", "mlops", "머신러닝")) {
            return JobRole.ML_ENGINEER;
        }

        if (matchesAny(text, "ai engineer", "llm", "genai", "ai 엔지니어", "인공지능")) {
            return JobRole.AI_ENGINEER;
        }

        if (matchesAny(text, "data engineer", "data pipeline", "etl", "데이터 엔지니어", "데이터 파이프라인")) {
            return JobRole.DATA_ENGINEER;
        }

        if (matchesAny(text, "devops", "sre", "platform engineer", "kubernetes", "k8s", "infra", "인프라", "플랫폼 엔지니어")) {
            return JobRole.DEVOPS;
        }

        if (matchesAny(text, "dba", "database administrator", "데이터베이스 관리자")) {
            return JobRole.DBA;
        }

        if (matchesAny(text, "security engineer", "security", "보안")) {
            return JobRole.SECURITY;
        }

        if (matchesAny(text, "qa", "test engineer", "quality assurance", "테스트 엔지니어", "품질")) {
            return JobRole.QA;
        }

        if (matchesAny(text, "product manager", "project manager", "pm", "po", "서비스 기획", "프로덕트 매니저")) {
            return JobRole.PM;
        }

        if (matchesAny(text, "frontend", "front end", "front-end", "react", "vue", "프론트엔드")) {
            return JobRole.FRONTEND;
        }

        if (matchesAny(text, "backend", "back end", "back-end", "server", "spring", "java", "백엔드", "서버")) {
            return JobRole.BACKEND;
        }

        return JobRole.ETC;
    }

    private List<String> nonNullTexts(String... texts) {
        if (texts == null) {
            return List.of();
        }

        return Arrays.stream(texts)
                .filter(text -> text != null && !text.isBlank())
                .toList();
    }

    private boolean matchesAny(String text, String... keywords) {
        return Arrays.stream(keywords)
                .map(this::normalize)
                .anyMatch(keyword -> matches(text, keyword));
    }

    private boolean matches(String text, String keyword) {
        if (keyword.isBlank()) {
            return false;
        }

        if (keyword.matches("[a-z0-9+#.]+")) {
            return (" " + text + " ").contains(" " + keyword + " ");
        }

        return text.contains(keyword);
    }

    private String normalize(String text) {
        return NON_WORD_PATTERN.matcher(text.toLowerCase(Locale.ROOT))
                .replaceAll(" ")
                .trim()
                .replaceAll("\\s+", " ");
    }
}
