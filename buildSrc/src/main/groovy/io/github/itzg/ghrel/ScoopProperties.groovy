package io.github.itzg.ghrel

import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.provider.ProviderFactory

import javax.inject.Inject

abstract class ScoopProperties {

    abstract Property<String> getRepository();

    abstract Property<String> getBucketDirectory();

    /**
     * @return specific branch or defaults to repo's default branch
     */
    abstract Property<String> getBranch();

    @Inject
    ScoopProperties(Project project) {
        final ProviderFactory providers = project.providers;
        repository.convention(
           providers.gradleProperty("scoopBucketRepo")
               .orElse(providers.environmentVariable("SCOOP_BUCKET_REPO"))
        );
        bucketDirectory.convention("bucket");
    }
}
