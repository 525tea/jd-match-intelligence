package jobflow.domain.project.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import jobflow.domain.project.UserProject;
import jobflow.domain.project.UserProjectAnalysis;

public record ProjectSummaryResponse(
        Long userProjectId,
        String name,
        String sourceType,
        String externalId,
        String repositoryFullName,
        String repositoryUrl,
        String description,
        Long analysisId,
        int analysisVersion,
        String ref,
        LocalDateTime analyzedAt,
        Integer commitCount,
        Integer fileCount,
        Integer contributorCount,
        List<ProjectDirectoryStatResponse> directories
) implements Serializable {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static ProjectSummaryResponse from(
            UserProject userProject,
            UserProjectAnalysis analysis
    ) {
        RawAnalysisSummary rawAnalysisSummary =
                RawAnalysisSummary.from(analysis == null ? null : analysis.getRawAnalysis());
        return new ProjectSummaryResponse(
                userProject.getId(),
                userProject.getName(),
                userProject.getSourceType().name(),
                userProject.getExternalId(),
                userProject.getExternalId(),
                userProject.getRepositoryUrl(),
                userProject.getDescription(),
                analysis == null ? null : analysis.getId(),
                analysis == null ? 0 : analysis.getAnalysisVersion(),
                firstNonBlank(rawAnalysisSummary.ref(), analysis == null ? null : analysis.getCommitSha()),
                analysis == null ? null : analysis.getAnalyzedAt(),
                rawAnalysisSummary.commitCount(),
                rawAnalysisSummary.fileCount(),
                rawAnalysisSummary.contributorCount(),
                rawAnalysisSummary.directories()
        );
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second == null || second.isBlank() ? null : second;
    }

    private record RawAnalysisSummary(
            String ref,
            Integer commitCount,
            Integer fileCount,
            Integer contributorCount,
            List<ProjectDirectoryStatResponse> directories
    ) {

        static RawAnalysisSummary empty() {
            return new RawAnalysisSummary(null, null, null, null, List.of());
        }

        static RawAnalysisSummary from(String rawAnalysis) {
            if (rawAnalysis == null || rawAnalysis.isBlank()) {
                return empty();
            }

            try {
                JsonNode root = OBJECT_MAPPER.readTree(rawAnalysis);
                String ref = blankToNull(root.path("ref").asText(null));
                JsonNode stats = root.path("repositoryStats");
                if (stats.isMissingNode() || stats.isNull()) {
                    return new RawAnalysisSummary(
                            ref,
                            null,
                            legacyFoundFileCount(root),
                            null,
                            List.of()
                    );
                }

                List<ProjectDirectoryStatResponse> directories = new ArrayList<>();
                JsonNode directoryNodes = stats.path("directories");
                if (directoryNodes.isArray()) {
                    for (JsonNode node : directoryNodes) {
                        directories.add(new ProjectDirectoryStatResponse(
                                node.path("path").asText(""),
                                node.path("fileCount").asInt(0),
                                node.path("share").asInt(0)
                        ));
                    }
                }

                return new RawAnalysisSummary(
                        ref,
                        nullableInt(stats, "commitCount"),
                        nullableInt(stats, "fileCount"),
                        nullableInt(stats, "contributorCount"),
                        directories
                );
            } catch (Exception ignored) {
                return empty();
            }
        }

        private static Integer legacyFoundFileCount(JsonNode root) {
            int foundFileCount = root.path("buildFileAnalysis").path("foundFileCount").asInt(0)
                    + root.path("infraFileAnalysis").path("foundFileCount").asInt(0)
                    + root.path("workflowFileAnalysis").path("foundFileCount").asInt(0);
            return foundFileCount == 0 ? null : foundFileCount;
        }

        private static Integer nullableInt(JsonNode node, String fieldName) {
            JsonNode value = node.path(fieldName);
            return value.isMissingNode() || value.isNull() ? null : value.asInt();
        }

        private static String blankToNull(String value) {
            return value == null || value.isBlank() ? null : value;
        }
    }
}
