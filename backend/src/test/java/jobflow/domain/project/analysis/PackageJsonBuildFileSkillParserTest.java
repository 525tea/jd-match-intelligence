package jobflow.domain.project.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PackageJsonBuildFileSkillParserTest {

    private final PackageJsonBuildFileSkillParser parser = new PackageJsonBuildFileSkillParser();

    @Test
    @DisplayName("package.json dependencies에서 skill 후보를 추출한다")
    void parse() {
        RepositoryBuildFile buildFile = RepositoryBuildFile.fromPath(
                "frontend/package.json",
                """
                {
                  "dependencies": {
                    "next": "15.0.0",
                    "react": "19.0.0",
                    "typescript": "5.0.0",
                    "tailwindcss": "4.0.0",
                    "@nestjs/core": "11.0.0"
                  },
                  "devDependencies": {
                    "jest": "30.0.0",
                    "eslint": "9.0.0"
                  }
                }
                """
        );

        List<BuildFileSkillCandidate> candidates = parser.parse(buildFile);

        assertThat(candidates)
                .extracting(BuildFileSkillCandidate::skillName)
                .contains(
                        "Next.js",
                        "React",
                        "TypeScript",
                        "Tailwind CSS",
                        "NestJS",
                        "Jest",
                        "ESLint"
                );
    }
}
