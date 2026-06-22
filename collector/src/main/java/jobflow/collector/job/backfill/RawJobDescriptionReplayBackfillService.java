package jobflow.collector.job.backfill;

import java.util.List;
import jobflow.collector.job.Job;
import jobflow.collector.job.JobRepository;
import jobflow.collector.job.JobRole;
import jobflow.collector.job.ingest.FetchedJobPosting;
import jobflow.collector.job.ingest.IngestedJobPosting;
import jobflow.collector.job.ingest.JobExperienceTagNormalizationService;
import jobflow.collector.job.ingest.JobIngestionSource;
import jobflow.collector.job.ingest.JobPostingParseException;
import jobflow.collector.job.ingest.JobPostingParser;
import jobflow.collector.job.ingest.JobSkillNormalizationService;
import jobflow.collector.job.snapshot.RawJobSnapshotStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class RawJobDescriptionReplayBackfillService {

    private final JobRepository jobRepository;
    private final ObjectMapper objectMapper;
    private final List<JobPostingParser> parsers;
    private final JobSkillNormalizationService jobSkillNormalizationService;
    private final JobExperienceTagNormalizationService jobExperienceTagNormalizationService;
    private final RawJobSnapshotStorage rawJobSnapshotStorage;

    @Transactional
    public RawJobDescriptionReplayBackfillSummary backfill(List<String> sources) {
        List<Job> jobs = jobRepository.findBySourceInOrderByIdAsc(sources);

        int updatedDescriptionCount = 0;
        int unchangedDescriptionCount = 0;
        int updatedRoleCount = 0;
        int skippedCount = 0;
        int failedCount = 0;
        int normalizedSkillJobCount = 0;
        int normalizedExperienceTagJobCount = 0;

        for (Job job : jobs) {
            ReplayResult replayResult = replay(job);

            if (replayResult.status() == ReplayStatus.SKIPPED) {
                skippedCount++;
                continue;
            }

            if (replayResult.status() == ReplayStatus.FAILED) {
                failedCount++;
                continue;
            }

            String replayedDescription = replayResult.description();

            if (sameDescription(job.getDescription(), replayedDescription)) {
                unchangedDescriptionCount++;
            } else {
                job.updateDescription(replayedDescription);
                updatedDescriptionCount++;
            }

            if (replayResult.role() != null && replayResult.role() != job.getRole()) {
                job.updateRole(replayResult.role());
                updatedRoleCount++;
            }

            int skillCount = jobSkillNormalizationService.replaceNormalizedSkills(
                    job,
                    job.getTitle(),
                    job.getDescription(),
                    job.getRoleDetail()
            ).size();

            if (skillCount > 0) {
                normalizedSkillJobCount++;
            }

            int experienceTagCount = jobExperienceTagNormalizationService.replaceNormalizedExperienceTags(
                    job,
                    job.getTitle(),
                    job.getDescription(),
                    job.getRoleDetail()
            ).size();

            if (experienceTagCount > 0) {
                normalizedExperienceTagJobCount++;
            }
        }

        return new RawJobDescriptionReplayBackfillSummary(
                jobs.size(),
                updatedDescriptionCount,
                unchangedDescriptionCount,
                updatedRoleCount,
                skippedCount,
                failedCount,
                normalizedSkillJobCount,
                normalizedExperienceTagJobCount
        );
    }

    private ReplayResult replay(Job job) {
        String rawData = rawDataFor(job);

        if (rawData == null || rawData.isBlank()) {
            log.warn("Raw description replay skipped. reason=missing_raw_data_and_snapshot, jobId={}, source={}, externalId={}, rawSnapshotKey={}",
                    job.getId(),
                    job.getSource(),
                    job.getExternalId(),
                    job.getRawSnapshotKey());
            return ReplayResult.skipped();
        }

        JobIngestionSource source;

        try {
            source = JobIngestionSource.valueOf(job.getSource());
        } catch (IllegalArgumentException exception) {
            log.warn("Raw description replay skipped. reason=unsupported_source, jobId={}, source={}, externalId={}",
                    job.getId(),
                    job.getSource(),
                    job.getExternalId());
            return ReplayResult.skipped();
        }

        JobPostingParser parser = parserFor(source);

        if (parser == null) {
            log.warn("Raw description replay skipped. reason=parser_not_found, jobId={}, source={}, externalId={}",
                    job.getId(),
                    job.getSource(),
                    job.getExternalId());
            return ReplayResult.skipped();
        }

        try {
            String body = bodyFor(source, rawData);
            FetchedJobPosting fetched = new FetchedJobPosting(
                    source,
                    job.getExternalId(),
                    sourceUrl(job),
                    detailUrl(job),
                    body
            );
            IngestedJobPosting posting = parser.parse(fetched);

            if (posting.description() == null || posting.description().isBlank()) {
                log.warn("Raw description replay failed. reason=blank_replayed_description, jobId={}, source={}, externalId={}",
                        job.getId(),
                        job.getSource(),
                        job.getExternalId());
                return ReplayResult.failed();
            }

            return ReplayResult.updated(posting.description(), posting.role());
        } catch (JobPostingParseException | JacksonException exception) {
            log.warn("Raw description replay failed. jobId={}, source={}, externalId={}, error={}",
                    job.getId(),
                    job.getSource(),
                    job.getExternalId(),
                    exception.getMessage());
            return ReplayResult.failed();
        } catch (RuntimeException exception) {
            log.warn("Raw description replay failed unexpectedly. jobId={}, source={}, externalId={}, error={}",
                    job.getId(),
                    job.getSource(),
                    job.getExternalId(),
                    exception.getMessage());
            return ReplayResult.failed();
        }
    }

    private String rawDataFor(Job job) {
        if (job.getRawData() != null && !job.getRawData().isBlank()) {
            return job.getRawData();
        }

        if (job.getRawSnapshotKey() == null || job.getRawSnapshotKey().isBlank()) {
            return null;
        }

        return rawJobSnapshotStorage.read(job.getRawSnapshotKey());
    }

    private JobPostingParser parserFor(JobIngestionSource source) {
        return parsers.stream()
                .filter(parser -> parser.supports(source))
                .findFirst()
                .orElse(null);
    }

    private String bodyFor(JobIngestionSource source, String rawData) throws JacksonException {
        if (source == JobIngestionSource.JUMPIT) {
            JsonNode root = objectMapper.readTree(rawData);
            return root.path("rawBody").asText("");
        }

        return rawData;
    }

    private String sourceUrl(Job job) {
        if (job.getOriginalUrl() != null && !job.getOriginalUrl().isBlank()) {
            return job.getOriginalUrl();
        }

        if (job.getUrl() != null && !job.getUrl().isBlank()) {
            return job.getUrl();
        }

        return "";
    }

    private String detailUrl(Job job) {
        if (job.getOriginalUrl() != null && !job.getOriginalUrl().isBlank()) {
            return job.getOriginalUrl();
        }

        if (job.getUrl() != null && !job.getUrl().isBlank()) {
            return job.getUrl();
        }

        return "";
    }

    private boolean sameDescription(String before, String after) {
        return normalizeLineEndings(before).equals(normalizeLineEndings(after));
    }

    private String normalizeLineEndings(String value) {
        if (value == null) {
            return "";
        }

        return value.replace("\r\n", "\n")
                .replace('\r', '\n')
                .trim();
    }

    private enum ReplayStatus {
        UPDATED,
        SKIPPED,
        FAILED
    }

    private record ReplayResult(
            ReplayStatus status,
            String description,
            JobRole role
    ) {

        static ReplayResult updated(String description, JobRole role) {
            return new ReplayResult(ReplayStatus.UPDATED, description, role);
        }

        static ReplayResult skipped() {
            return new ReplayResult(ReplayStatus.SKIPPED, null, null);
        }

        static ReplayResult failed() {
            return new ReplayResult(ReplayStatus.FAILED, null, null);
        }
    }
}
