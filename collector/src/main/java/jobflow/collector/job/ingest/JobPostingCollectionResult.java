package jobflow.collector.job.ingest;

public record JobPostingCollectionResult(
        CrawlerUrlCandidate candidate,
        JobIngestionResultType ingestionResultType,
        boolean success,
        boolean hasDuplicateCandidates,
        int duplicateCandidateCount,
        String errorMessage
) {

    public static JobPostingCollectionResult success(
            CrawlerUrlCandidate candidate,
            JobIngestionResult ingestionResult
    ) {
        return new JobPostingCollectionResult(
                candidate,
                ingestionResult.type(),
                true,
                ingestionResult.hasDuplicateCandidates(),
                ingestionResult.duplicateCandidates().size(),
                null
        );
    }

    public static JobPostingCollectionResult success(
            CrawlerUrlCandidate candidate,
            JobIngestionResultType ingestionResultType
    ) {
        return new JobPostingCollectionResult(
                candidate,
                ingestionResultType,
                true,
                false,
                0,
                null
        );
    }

    public static JobPostingCollectionResult failure(
            CrawlerUrlCandidate candidate,
            Exception exception
    ) {
        return new JobPostingCollectionResult(
                candidate,
                null,
                false,
                false,
                0,
                toErrorMessage(exception)
        );
    }

    private static String toErrorMessage(Exception exception) {
        String message = exception.getMessage();

        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }

        return message;
    }
}
