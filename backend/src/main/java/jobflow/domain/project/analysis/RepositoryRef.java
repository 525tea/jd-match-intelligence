package jobflow.domain.project.analysis;

public record RepositoryRef(
        String owner,
        String name,
        String ref
) {

    public RepositoryRef {
        if (owner == null || owner.isBlank()) {
            throw new IllegalArgumentException("owner must not be blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (ref == null || ref.isBlank()) {
            ref = "HEAD";
        }
    }

    public static RepositoryRef of(String owner, String name) {
        return new RepositoryRef(owner, name, "HEAD");
    }

    public String fullName() {
        return owner + "/" + name;
    }
}
