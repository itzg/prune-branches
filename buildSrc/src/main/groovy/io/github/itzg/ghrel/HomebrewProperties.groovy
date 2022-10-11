package io.github.itzg.ghrel

import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.provider.ProviderFactory

import javax.inject.Inject

abstract class HomebrewProperties {
    abstract Property<String> getRepository();

    /**
     * @return specific branch or defaults to repo's default branch
     */
    abstract Property<String> getBranch();

    abstract Property<String> getJavaDependency();

    @Inject
    HomebrewProperties(Project project) {
        final ProviderFactory providers = project.providers;
        repository.convention(
            providers.gradleProperty("homebrewTapRepo")
                .orElse(providers.environmentVariable("HOMEBREW_TAP_REPO"))
        );

        javaDependency.convention('java')
    }
}
