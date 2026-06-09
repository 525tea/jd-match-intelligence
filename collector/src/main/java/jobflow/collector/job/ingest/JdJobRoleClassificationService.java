package jobflow.collector.job.ingest;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import jobflow.collector.job.JobRole;
import org.springframework.stereotype.Service;

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
        List<String> textParts = nonNullTexts(texts);
        String text = normalize(String.join(" ", textParts));
        JobRole titleRole = classifyTitle(textParts);

        if (titleRole != null) {
            return titleRole;
        }

        if (matchesAny(text, "게임 서버", "게임개발 서버", "game server")) {
            return JobRole.GAME_SERVER;
        }

        if (matchesAny(text, "게임 클라이언트", "게임개발 클라이언트", "game client")) {
            return JobRole.GAME_CLIENT;
        }

        if (matchesAny(text, "게임 모바일", "게임개발 모바일", "mobile game")) {
            return JobRole.GAME_MOBILE;
        }

        if (matchesAny(text, "게임 qa", "게임qa", "game qa")) {
            return JobRole.GAME_QA;
        }

        if (matchesAny(text, "게임 기획", "게임기획", "게임 pm", "game pm")) {
            return JobRole.GAME_PM;
        }

        if (matchesAny(text, "게임 운영", "게임운영", "game operation")) {
            return JobRole.GAME_OPERATION;
        }

        if (matchesAny(text, "technical artist", "테크니컬 아티스트", "테크니컬아티스트")) {
            return JobRole.TECHNICAL_ARTIST;
        }

        if (matchesAny(text, "게임 아트", "게임아트", "game art")) {
            return JobRole.GAME_ART;
        }

        if (matchesAny(text, "3d modeling", "3d 모델링", "게임 3d", "게임3d")) {
            return JobRole.GAME_3D_MODELING;
        }

        if (matchesAny(text, "animation", "애니메이션", "게임 애니메이션", "게임애니메이션")) {
            return JobRole.GAME_ANIMATION;
        }

        if (matchesAny(text, "effect", "이펙트", "fx", "게임 이펙트", "게임이펙트")) {
            return JobRole.GAME_EFFECT;
        }

        if (matchesAny(text, "게임 인터페이스", "게임인터페이스", "game interface")) {
            return JobRole.GAME_INTERFACE;
        }

        if (matchesAny(text, "게임 영상", "게임 연출", "게임연출", "game directing")) {
            return JobRole.GAME_DIRECTING_VIDEO;
        }

        if (matchesAny(text, "게임 사운드", "게임사운드", "game sound")) {
            return JobRole.GAME_SOUND;
        }

        if (matchesAny(text, "product manager", "project manager", "program manager", "pmo", "pm", "po",
                "project management", "서비스 기획", "프로덕트 매니저", "개발매니저", "개발 매니저", "cto",
                "chief technology officer", "기술 총괄")) {
            return JobRole.PM;
        }

        if (matchesAny(text, "frontend", "frontendengineer", "front end", "front-end", "react", "vue",
                "next.js", "nextjs", "프론트엔드")) {
            return JobRole.FRONTEND;
        }

        if (matchesAny(text, "backend", "backendengineer", "back end", "back-end", "server", "spring", "java",
                "백엔드", "서버", "api 개발", "api개발", "back end개발")) {
            return JobRole.BACKEND;
        }

        if (matchesAny(text, "security engineer", "security", "cybersecurity", "cyber security", "보안", "사이버보안",
                "패치 관리", "patch management")) {
            return JobRole.SECURITY;
        }

        if (matchesAny(text, "sap", "erp", "abap", "si sm", "si/sm", "sm 모듈", "erp 컨설팅",
                "erp 시스템", "기간계", "그룹웨어")) {
            return JobRole.ERP_SAP;
        }

        if (matchesAny(text, "autonomous driving", "자율주행", "uav autonomy")) {
            return JobRole.AUTONOMOUS_DRIVING;
        }

        if (matchesAny(text, "robot", "robotics", "로봇", "ros", "slam", "manipulation", "motion",
                "robot control", "robot controls", "로봇 제어", "로봇 응용", "mobile robot")) {
            return JobRole.ROBOT_SOFTWARE;
        }

        if (matchesAny(text, "ai engineer", "ai developer", "ai 개발자", "ai 엔지니어", "인공지능")) {
            return JobRole.AI_ENGINEER;
        }

        if (matchesAny(text, "소프트웨어 엔지니어", "소프트웨어 개발자", "s/w 소프트웨어 개발")) {
            return JobRole.SOFTWARE_ENGINEER;
        }

        if (matchesAny(text, "embedded", "임베디드", "firmware", "펌웨어", "fw 개발", "fw개발",
                "embedded c", "rtos", "autosar", "rtl design", "rtl", "soc", "nvr", "pc sw", "web viewer")) {
            return JobRole.EMBEDDED_SOFTWARE;
        }

        if (matchesAny(text, "hardware engineer", "하드웨어 엔지니어", "하드웨어엔지니어", "회로개발", "회로 개발",
                "antenna", "안테나", "phased array", "rf engineer")) {
            return JobRole.HARDWARE_ENGINEER;
        }

        if (matchesAny(text, "system software", "시스템 소프트웨어", "시스템소프트웨어", "system sw",
                "s/w 시스템", "runtime software", "runtime 소프트웨어")) {
            return JobRole.SYSTEM_SOFTWARE;
        }

        if (matchesAny(text, "android", "안드로이드")) {
            return JobRole.ANDROID;
        }

        if (matchesAny(text, "ios", "iphone", "swift", "objective c", "아이폰")) {
            return JobRole.IOS;
        }

        if (matchesAny(text, "fullstack", "full stack", "full-stack", "풀스택")) {
            return JobRole.FULLSTACK;
        }

        if (matchesAny(text, "sre", "site reliability")) {
            return JobRole.SRE;
        }

        if (matchesAny(text, "devops", "platform engineer", "kubernetes", "k8s", "infra", "infrastructure",
                "cloud engineer", "클라우드 엔지니어", "인프라", "플랫폼 엔지니어", "linux", "리눅스",
                "aws/idc", "ci/cd")) {
            return JobRole.DEVOPS;
        }

        if (matchesAny(text, "network engineer", "네트워크 엔지니어", "시스템 네트워크", "시스템·네트워크",
                "system engineer", "시스템 엔지니어", "시스템운영", "네트워크")) {
            return JobRole.SYSTEM_NETWORK;
        }

        if (matchesAny(text, "data analyst", "데이터 분석가", "데이터분석가", "데이터 분석")) {
            return JobRole.DATA_ANALYST;
        }

        if (matchesAny(text, "data scientist", "데이터 사이언티스트", "데이터사이언티스트", "데이터과학",
                "data science")) {
            return JobRole.DATA_SCIENTIST;
        }

        if (matchesAny(text, "data engineer", "data pipeline", "etl", "데이터 엔지니어", "데이터 파이프라인")) {
            return JobRole.DATA_ENGINEER;
        }

        if (matchesAny(text, "computer vision", "컴퓨터비전", "비전", "vision", "perception", "yolo",
                "image processing", "이미지 프로세싱")) {
            return JobRole.COMPUTER_VISION;
        }

        if (matchesAny(text, "영상 음성", "영상·음성", "음성 ai", "speech", "voice", "audio")) {
            return JobRole.VISION_AUDIO_AI;
        }

        if (matchesAny(text, "nlp", "natural language")) {
            return JobRole.NLP;
        }

        if (matchesAny(text, "llm", "large language model")) {
            return JobRole.LLM;
        }

        if (matchesAny(text, "mlops")) {
            return JobRole.MLOPS;
        }

        if (matchesAny(text, "rag")) {
            return JobRole.RAG;
        }

        if (matchesAny(text, "generative ai", "genai", "생성형 ai", "생성형ai")) {
            return JobRole.GENERATIVE_AI;
        }

        if (matchesAny(text, "multimodal", "멀티모달")) {
            return JobRole.MULTIMODAL_ENGINEER;
        }

        if (matchesAny(text, "ai researcher", "ai research", "ai 리서치", "ai 연구", "research engineer",
                "researcher", "physical ai")) {
            return JobRole.AI_RESEARCHER;
        }

        if (matchesAny(text, "machine learning", "ml engineer", "reinforcement learning", "deep learning",
                "머신러닝", "강화학습", "딥러닝")) {
            return JobRole.ML_ENGINEER;
        }

        if (matchesAny(text, "ai business", "ai 비즈니스", "ai비즈니스")) {
            return JobRole.AI_BUSINESS;
        }

        if (matchesAny(text, "ai 서비스 기획", "ai서비스기획")) {
            return JobRole.AI_SERVICE_PLANNING;
        }

        if (matchesAny(text, "dba", "database administrator", "데이터베이스 관리자")) {
            return JobRole.DBA;
        }

        if (matchesAny(text, "qa", "test engineer", "quality assurance", "테스트 엔지니어", "품질")) {
            return JobRole.QA;
        }

        if (matchesAny(text, "cross platform", "크로스플랫폼", "react native", "flutter", "xamarin")) {
            return JobRole.CROSS_PLATFORM;
        }

        if (matchesAny(text, "iot", "사물인터넷")) {
            return JobRole.IOT;
        }

        if (matchesAny(text, "blockchain", "블록체인", "smart contract", "web3")) {
            return JobRole.BLOCKCHAIN;
        }

        if (matchesAny(text, "web publishing", "웹퍼블리싱", "웹 퍼블리싱", "퍼블리셔", "publisher")) {
            return JobRole.WEB_PUBLISHING;
        }

        if (matchesAny(text, "vr", "ar", "xr", "3d", "digital twin", "디지털 트윈", "메타버스")) {
            return JobRole.VR_AR_3D;
        }

        if (matchesAny(text, "graphics", "그래픽스", "rendering", "렌더링", "shader", "쉐이더")) {
            return JobRole.GRAPHICS;
        }

        if (matchesAny(text, "application sw", "application software", "응용프로그램", "응용 프로그램",
                "application 개발", "application기능", "application ui", "web개발", "web 개발")) {
            return JobRole.APPLICATION_SOFTWARE;
        }

        if (matchesAny(text, "software engineer", "software developer", "소프트웨어 엔지니어", "소프트웨어 개발자",
                "sw engineer", "sw 개발", "s/w 소프트웨어 개발", "s/w software")) {
            return JobRole.SOFTWARE_ENGINEER;
        }

        return JobRole.ETC;
    }

    private JobRole classifyTitle(List<String> textParts) {
        if (textParts.isEmpty()) {
            return null;
        }

        String title = normalize(textParts.get(0));

        if (matchesAny(title, "게임 서버", "게임개발 서버", "game server")) {
            return JobRole.GAME_SERVER;
        }

        if (matchesAny(title, "게임 클라이언트", "게임개발 클라이언트", "game client")) {
            return JobRole.GAME_CLIENT;
        }

        if (matchesAny(title, "게임 모바일", "게임개발 모바일", "mobile game")) {
            return JobRole.GAME_MOBILE;
        }

        if (matchesAny(title, "product manager", "project manager", "program manager", "project management",
                "pmo", "pm", "po", "개발매니저", "개발 매니저", "cto", "chief technology officer", "기술 총괄")) {
            return JobRole.PM;
        }

        if (matchesAny(title, "fullstack", "full stack", "full-stack", "풀스택")) {
            return JobRole.FULLSTACK;
        }

        if (matchesAny(title, "frontend", "frontendengineer", "front end", "front-end", "프론트엔드")) {
            return JobRole.FRONTEND;
        }

        if (matchesAny(title, "backend", "backendengineer", "back end", "back-end", "백엔드", "서버")) {
            return JobRole.BACKEND;
        }

        if (matchesAny(title, "security engineer", "security", "cybersecurity", "cyber security", "보안", "사이버보안")) {
            return JobRole.SECURITY;
        }

        if (matchesAny(title, "sap", "erp", "abap", "si sm", "si/sm")) {
            return JobRole.ERP_SAP;
        }

        if (matchesAny(title, "autonomous driving", "자율주행", "uav autonomy")) {
            return JobRole.AUTONOMOUS_DRIVING;
        }

        if (matchesAny(title, "robot", "robotics", "로봇", "ros", "slam", "robot control", "robot controls")) {
            return JobRole.ROBOT_SOFTWARE;
        }

        if (matchesAny(title, "ai engineer", "ai developer", "ai 개발자", "ai 엔지니어")) {
            return JobRole.AI_ENGINEER;
        }

        if (matchesAny(title, "embedded", "임베디드", "firmware", "펌웨어", "fw 개발", "fw개발",
                "rtl", "soc", "nvr")) {
            return JobRole.EMBEDDED_SOFTWARE;
        }

        if (matchesAny(title, "hardware engineer", "하드웨어 엔지니어", "하드웨어엔지니어", "회로개발", "회로 개발",
                "antenna", "안테나", "phased array")) {
            return JobRole.HARDWARE_ENGINEER;
        }

        if (matchesAny(title, "소프트웨어 엔지니어", "소프트웨어 개발자", "s/w 소프트웨어 개발")) {
            return JobRole.SOFTWARE_ENGINEER;
        }

        return null;
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
