package jobflow.collector.job.ingest;

public record RobotsPathRule(
        boolean allow,
        String path
) {

    public int length() {
        return path == null ? 0 : path.length();
    }
}
