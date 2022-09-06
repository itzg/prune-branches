package io.github.itzg.ghrel;

import javax.inject.Inject;
import org.gradle.api.Project;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.ProviderFactory;

abstract class ScoopProperties {

    public abstract Property<String> getRepository();

    public abstract Property<String> getBucketDirectory();

    /**
     * @return specific branch or defaults to repo's default branch
     */
    abstract Property<String> getBranch();

    @Inject
    public ScoopProperties(Project project) {
        final ProviderFactory providers = project.getProviders();
        getRepository().convention(
           providers.gradleProperty("scoopBucketRepo")
               .orElse(providers.environmentVariable("SCOOP_BUCKET_REPO"))
        );
        getBucketDirectory().convention("bucket");
    }
}
