package jobflow.domain.project;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jobflow.domain.common.BaseTimeEntity;
import jobflow.domain.user.User;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "user_projects")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserProject extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ProjectSourceType sourceType = ProjectSourceType.GITHUB;

    @Column(length = 200)
    private String externalId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 500)
    private String repositoryUrl;

    @Column(length = 1000)
    private String description;
}
