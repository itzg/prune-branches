package io.github.itzg.ghrel;

import javax.inject.Inject;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.ProviderFactory;

abstract class GithubReleaserExtension {

    abstract Property<String> getReleaseName();

    abstract Property<String> getGithubApiUrl();

    abstract Property<String> getGithubRepository();

    abstract Property<String> getGithubToken();

    private final PublishProperties publishProperties;

    @Inject
    public GithubReleaserExtension(Project project) {
        this.publishProperties = project.getObjects().newInstance(PublishProperties.class);
        final ProviderFactory providers = project.getProviders();

        getReleaseName().convention(
            providers.gradleProperty("releaseName")
                .orElse(providers.environmentVariable("GITHUB_REF_NAME"))
        );
        getGithubApiUrl().convention(
            providers.environmentVariable("GITHUB_API_URL")
                .orElse("https://api.github.com")
        );
        getGithubRepository().convention(
            providers.gradleProperty("repository")
                .orElse(providers.environmentVariable("GITHUB_REPOSITORY"))
        );
        getGithubToken().convention(
            providers.environmentVariable("GITHUB_TOKEN")
        );
    }

    public void publish(Action<PublishProperties> action) {
        action.execute(publishProperties);
    }

    public PublishProperties getPublish() {
        return publishProperties;
    }
}
