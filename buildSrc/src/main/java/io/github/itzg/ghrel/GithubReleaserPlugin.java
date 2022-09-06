package io.github.itzg.ghrel;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.api.tasks.bundling.Tar;
import org.gradle.api.tasks.bundling.Zip;

public class GithubReleaserPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        project.getPluginManager().withPlugin("application", appliedPlugin -> {
            final GithubReleaserExtension extension = project.getExtensions()
                .create("githubReleaser", GithubReleaserExtension.class);

            final TaskProvider<ReleaseApplicationTask> releaseTask =
                project.getTasks().register("githubReleaseApplication", ReleaseApplicationTask.class, task -> {
                    task.setGroup("distribution");
                    task.apply(extension);

                    task.getApplicationTar().set(
                        project.getTasks().named("distTar", Tar.class)
                            .flatMap(AbstractArchiveTask::getArchiveFile)
                    );
                    task.getApplicationZip().set(
                        project.getTasks().named("distZip", Zip.class)
                            .flatMap(AbstractArchiveTask::getArchiveFile)
                    );
                });

            project.getTasks().register("githubPublishApplication", PublishApplicationTask.class, task -> {
                task.setGroup("distribution");
                task.apply(extension);
                task.dependsOn(releaseTask);

                task.getAssets().set(releaseTask.flatMap(ReleaseApplicationTask::getAssets));
                final TaskProvider<Zip> distZip = project.getTasks().named("distZip", Zip.class);
                task.getZipTopDirectory().set(
                    distZip
                        .flatMap(it -> project.getProviders().provider(() ->
                            it.getArchiveBaseName().get() + "-" + it.getArchiveVersion().get()))
                );
                task.getApplicationName().set(project.getName());
                task.getApplicationVersion().set(
                    distZip.flatMap(AbstractArchiveTask::getArchiveVersion)
                );
            });
        });
    }
}
