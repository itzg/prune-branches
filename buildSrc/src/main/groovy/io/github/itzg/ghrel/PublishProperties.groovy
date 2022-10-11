package io.github.itzg.ghrel

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.provider.ProviderFactory

import javax.inject.Inject

abstract class PublishProperties {
    abstract Property<String> getGithubPublishToken();

    private final ScoopProperties scoopProperties;

    private final HomebrewProperties homebrewProperties;

    @Inject
    PublishProperties(Project project) {
        this.scoopProperties = project.objects.newInstance(ScoopProperties);
        this.homebrewProperties = project.objects.newInstance(HomebrewProperties);

        final ProviderFactory providers = project.providers;
        githubPublishToken.convention(
            providers.gradleProperty("githubPublishToken")
                    .orElse(providers.environmentVariable("GITHUB_PUBLISH_TOKEN"))
        );
    }

    /**
     * Provides DSL-nested syntax
     */
    @SuppressWarnings('unused')
    void scoop(Action<ScoopProperties> action) {
        action.execute(scoopProperties);
    }

    ScoopProperties getScoop() {
        return scoopProperties;
    }

    void homebrew(Action<HomebrewProperties> action) {
        action.execute(homebrewProperties)
    }

    HomebrewProperties getHomebrew() {
        return homebrewProperties
    }
}
