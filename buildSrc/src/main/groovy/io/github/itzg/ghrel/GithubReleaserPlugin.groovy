package io.github.itzg.ghrel

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.bundling.Tar
import org.gradle.api.tasks.bundling.Zip

@SuppressWarnings('unused')
class GithubReleaserPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        project.pluginManager.withPlugin("application", appliedPlugin -> {
            final GithubReleaserExtension extension = project.extensions
                .create("githubReleaser", GithubReleaserExtension.class);

            final TaskProvider<ReleaseApplicationTask> releaseTask =
                project.tasks.register("githubReleaseApplication", ReleaseApplicationTask, task -> {
                    task.group = "distribution";
                    task.apply(extension);

                    task.applicationTar.set(
                        project.tasks.named("distTar", Tar.class)
                            .flatMap(AbstractArchiveTask::getArchiveFile)
                    );
                    task.applicationZip.set(
                        project.tasks.named("distZip", Zip.class)
                            .flatMap(AbstractArchiveTask::getArchiveFile)
                    );
                });

            project.tasks.register("githubPublishApplication", PublishApplicationTask, task -> {
                task.group = "distribution";
                task.apply(extension);
                task.dependsOn(releaseTask);

                task.assets.set(releaseTask.flatMap(ReleaseApplicationTask::getAssets));
                final TaskProvider<Zip> distZip = project.tasks.named("distZip", Zip.class);
                task.zipTopDirectory.set(
                    distZip
                        .flatMap(it -> project.providers.provider(() ->
                            it.archiveBaseName.get() + "-" + it.archiveVersion.get()))
                );
                task.applicationName.set(project.name);
                task.applicationVersion.set(
                    distZip.flatMap(AbstractArchiveTask::getArchiveVersion)
                );
            });
        });
    }
}
