package io.github.itzg.ghrel;

import javax.inject.Inject;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.ProviderFactory;

public abstract class PublishProperties {
    abstract Property<String> getGithubPublishToken();

    private final ScoopProperties scoopProperties;

    @Inject
    public PublishProperties(Project project) {
        this.scoopProperties = project.getObjects().newInstance(ScoopProperties.class);

        final ProviderFactory providers = project.getProviders();
        getGithubPublishToken().convention(
            providers.gradleProperty("githubPublishToken")
                    .orElse(providers.environmentVariable("GITHUB_PUBLISH_TOKEN"))
        );
    }

    public void scoop(Action<ScoopProperties> action) {
        action.execute(scoopProperties);
    }

    public ScoopProperties getScoop() {
        return scoopProperties;
    }
}
