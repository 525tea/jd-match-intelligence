package jobflow.domain.job.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import jobflow.domain.job.CareerLevel;
import jobflow.domain.job.JobRole;
import org.springframework.stereotype.Component;

@Component
public class JobSearchIntentParser {

    private static final Map<JobRole, List<String>> ROLE_KEYWORDS = Map.ofEntries(
            Map.entry(JobRole.BACKEND, List.of("backend", "back-end", "server", "서버", "백엔드")),
            Map.entry(JobRole.FRONTEND, List.of("frontend", "front-end", "react", "next", "프론트", "프론트엔드")),
            Map.entry(JobRole.FULLSTACK, List.of("fullstack", "full-stack", "풀스택")),
            Map.entry(JobRole.DEVOPS, List.of("devops", "infra", "infrastructure", "platform", "인프라", "플랫폼")),
            Map.entry(JobRole.SRE, List.of("sre", "site reliability")),
            Map.entry(JobRole.DATA_ENGINEER, List.of("data engineer", "데이터 엔지니어", "etl", "spark")),
            Map.entry(JobRole.DATA_ANALYST, List.of("data analyst", "데이터 분석")),
            Map.entry(JobRole.DATA_SCIENTIST, List.of("data scientist", "데이터 사이언티스트")),
            Map.entry(JobRole.ML_ENGINEER, List.of("ml engineer", "machine learning", "머신러닝")),
            Map.entry(JobRole.AI_ENGINEER, List.of("ai engineer", "인공지능", "딥러닝")),
            Map.entry(JobRole.SECURITY, List.of("security", "보안")),
            Map.entry(JobRole.QA, List.of("qa", "test engineer", "테스트")),
            Map.entry(JobRole.ANDROID, List.of("android", "안드로이드")),
            Map.entry(JobRole.IOS, List.of("ios", "swift")),
            Map.entry(JobRole.EMBEDDED_SOFTWARE, List.of("embedded", "firmware", "임베디드", "펌웨어")),
            Map.entry(JobRole.GAME_SERVER, List.of("game server", "게임 서버")),
            Map.entry(JobRole.GAME_CLIENT, List.of("game client", "게임 클라이언트"))
    );

    private static final Map<CareerLevel, List<String>> CAREER_KEYWORDS = Map.of(
            CareerLevel.NEWCOMER, List.of("newcomer", "entry", "신입"),
            CareerLevel.JUNIOR, List.of("junior", "주니어", "1년", "2년", "3년"),
            CareerLevel.MID, List.of("mid", "middle", "미들", "4년", "5년", "6년", "7년"),
            CareerLevel.SENIOR, List.of("senior", "시니어", "8년", "9년", "10년"),
            CareerLevel.LEAD, List.of("lead", "leader", "리드", "팀장")
    );

    private static final Map<String, List<String>> LOCATION_REGION_KEYWORDS = Map.ofEntries(
            Map.entry("Seoul", List.of("seoul", "서울")),
            Map.entry("Gyeonggi", List.of("gyeonggi", "경기", "성남", "판교", "수원", "용인")),
            Map.entry("Incheon", List.of("incheon", "인천")),
            Map.entry("Busan", List.of("busan", "부산")),
            Map.entry("Daegu", List.of("daegu", "대구")),
            Map.entry("Daejeon", List.of("daejeon", "대전")),
            Map.entry("Gwangju", List.of("gwangju", "광주")),
            Map.entry("Ulsan", List.of("ulsan", "울산")),
            Map.entry("Jeju", List.of("jeju", "제주"))
    );

    private static final Map<String, List<String>> REQUIRED_SKILL_KEYWORDS = Map.ofEntries(
            Map.entry("C++", List.of("c++", "cplusplus")),
            Map.entry("C#", List.of("c#", "csharp")),
            Map.entry("Node.js", List.of("node.js", "nodejs")),
            Map.entry(".NET", List.of(".net", "dotnet")),
            Map.entry("ASP.NET", List.of("asp.net", "aspnet")),
            Map.entry("Objective-C", List.of("objective-c", "objectivec")),
            Map.entry("Kubernetes", List.of("kubernetes", "k8s", "쿠버네티스")),
            Map.entry("React", List.of("react")),
            Map.entry("Spring Boot", List.of("spring boot", "스프링 부트"))
    );

    public JobSearchIntent parse(String keyword) {
        String normalizedKeyword = normalize(keyword);
        if (normalizedKeyword.isBlank()) {
            return new JobSearchIntent(List.of(), List.of(), List.of(), List.of());
        }

        return new JobSearchIntent(
                findMatchingRoles(normalizedKeyword),
                findMatchingCareerLevels(normalizedKeyword),
                findMatchingLocationRegions(normalizedKeyword),
                findMatchingRequiredSkillKeywords(normalizedKeyword)
        );
    }

    private List<JobRole> findMatchingRoles(String normalizedKeyword) {
        List<JobRole> roles = new ArrayList<>();
        ROLE_KEYWORDS.forEach((role, keywords) -> {
            if (containsAny(normalizedKeyword, keywords)) {
                roles.add(role);
            }
        });
        return List.copyOf(roles);
    }

    private List<CareerLevel> findMatchingCareerLevels(String normalizedKeyword) {
        List<CareerLevel> careerLevels = new ArrayList<>();
        CAREER_KEYWORDS.forEach((careerLevel, keywords) -> {
            if (containsAny(normalizedKeyword, keywords)) {
                careerLevels.add(careerLevel);
            }
        });
        return List.copyOf(careerLevels);
    }

    private List<String> findMatchingLocationRegions(String normalizedKeyword) {
        List<String> locationRegions = new ArrayList<>();
        LOCATION_REGION_KEYWORDS.forEach((locationRegion, keywords) -> {
            if (containsAny(normalizedKeyword, keywords)) {
                locationRegions.add(locationRegion);
            }
        });
        return List.copyOf(locationRegions);
    }

    private List<String> findMatchingRequiredSkillKeywords(String normalizedKeyword) {
        List<String> requiredSkillKeywords = new ArrayList<>();
        REQUIRED_SKILL_KEYWORDS.forEach((skillKeyword, keywords) -> {
            if (containsAny(normalizedKeyword, keywords)) {
                requiredSkillKeywords.add(skillKeyword);
            }
        });
        return List.copyOf(requiredSkillKeywords);
    }

    private boolean containsAny(String normalizedKeyword, List<String> keywords) {
        return keywords.stream()
                .map(this::normalize)
                .anyMatch(normalizedKeyword::contains);
    }

    private String normalize(String keyword) {
        return keyword == null
                ? ""
                : keyword.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }
}
