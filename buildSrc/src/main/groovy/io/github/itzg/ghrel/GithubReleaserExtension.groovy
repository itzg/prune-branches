package io.github.itzg.ghrel

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.provider.ProviderFactory

import javax.inject.Inject

abstract class GithubReleaserExtension {

    abstract Property<String> getReleaseName();

    abstract Property<String> getGithubApiUrl();

    abstract Property<String> getGithubRepository();

    abstract Property<String> getGithubToken();

    private final PublishProperties publishProperties;

    private final ProjectProperties projectProperties;

    @Inject
    GithubReleaserExtension(Project project) {
        this.publishProperties = project.objects.newInstance(PublishProperties);
        this.projectProperties = project.objects.newInstance(ProjectProperties)

        final ProviderFactory providers = project.providers;

        releaseName.convention(
            providers.gradleProperty("releaseName")
                .orElse(providers.environmentVariable("GITHUB_REF_NAME"))
        );
        githubApiUrl.convention(
            providers.environmentVariable("GITHUB_API_URL")
                .orElse("https://api.github.com")
        );
        githubRepository.convention(
            providers.gradleProperty("repository")
                .orElse(providers.environmentVariable("GITHUB_REPOSITORY"))
        );
        githubToken.convention(
            providers.environmentVariable("GITHUB_TOKEN")
        );
    }

    /**
     * Provides DSL-nested syntax
     */
    @SuppressWarnings('unused')
    void publish(Action<PublishProperties> action) {
        action.execute(publishProperties);
    }

    void project(Action<ProjectProperties> action) {
        action.execute(projectProperties)
    }

    PublishProperties getPublish() {
        return publishProperties;
    }

    ProjectProperties getProject() {
        return projectProperties
    }
}
