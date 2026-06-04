package jobflow.collector.job.ingest;

public record JobPostingCollectionResult(
        CrawlerUrlCandidate candidate,
        JobIngestionResultType ingestionResultType,
        boolean success,
        String errorMessage
) {

    public static JobPostingCollectionResult success(
            CrawlerUrlCandidate candidate,
            JobIngestionResultType ingestionResultType
    ) {
        return new JobPostingCollectionResult(candidate, ingestionResultType, true, null);
    }

    public static JobPostingCollectionResult failure(
            CrawlerUrlCandidate candidate,
            Exception exception
    ) {
        return new JobPostingCollectionResult(
                candidate,
                null,
                false,
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
