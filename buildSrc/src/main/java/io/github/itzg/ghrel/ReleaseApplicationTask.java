package io.github.itzg.ghrel;

import io.github.itzg.ghrel.Assets.Asset;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import javax.inject.Inject;
import org.apache.commons.codec.digest.DigestUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;
import org.kohsuke.github.GHAsset;
import org.kohsuke.github.GHRelease;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;

abstract class ReleaseApplicationTask extends DefaultTask {

    private GithubReleaserExtension extension;

    @InputFile
    abstract RegularFileProperty getApplicationTar();

    @InputFile
    abstract RegularFileProperty getApplicationZip();

    @Internal
    abstract Property<Assets> getAssets();

    @Inject
    public ReleaseApplicationTask() {
    }

    void apply(GithubReleaserExtension extension) {
        this.extension = extension;
    }

    @TaskAction
    public void releaseAndPublish() {
        try {
            final GitHub gitHub = new GitHubBuilder()
                .withEndpoint(extension.getGithubApiUrl().get())
                .withOAuthToken(extension.getGithubToken().get())
                .build();

            GHRelease release = getOurRelease(gitHub);

            final Assets assets = addArchives(release);
            getAssets().set(assets);

            //TODO generate release notes when this is addresed https://github.com/hub4j/github-api/issues/1498

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private GHRelease getOurRelease(GitHub gitHub) {
        final String repoName = extension.getGithubRepository().get();
        final GHRepository repository;
        try {
            repository = gitHub.getRepository(repoName);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to lookup repository given "+repoName, e);
        }


        final String tagOrReleaseName = extension.getReleaseName().get();
        final GHRelease release;
        try {
            release = findReleaseByName(repository, tagOrReleaseName);
            if (release != null) {
                return release;
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed while locating release", e);
        }

        try {
            return repository.createRelease(tagOrReleaseName)
                .name(tagOrReleaseName)
                .create();
        } catch (IOException e) {
            throw new RuntimeException("Failed to create release", e);
        }
    }

    private GHRelease findReleaseByName(GHRepository repository, String tagOrReleaseName) throws IOException {
        final GHRelease release = repository.getReleaseByTagName(tagOrReleaseName);
        if (release != null) {
            return release;
        }

        for (final GHRelease eachRelease : repository.listReleases()) {
            if (eachRelease.getName().equals(tagOrReleaseName)) {
                return eachRelease;
            }
        }
        return null;
    }

    private Assets addArchives(GHRelease release) throws IOException {
        final Asset zipAsset = uploadAsset(release, getApplicationZip());
        final Asset tarAsset = uploadAsset(release, getApplicationTar());

        return new Assets(zipAsset, tarAsset);
    }

    private Asset uploadAsset(GHRelease release, RegularFileProperty archive) throws IOException {
        final File file = archive.getAsFile().get();
        final String mimeType = Files.probeContentType(file.toPath());
        final String filename = file.getName();

        for (final GHAsset existing : release.listAssets()) {
            if (existing.getName().startsWith(filename)) {
                getLogger().info("Replacing existing asset {}", existing.getName());
                existing.delete();
            }
        }

        final String sha256;
        try (FileInputStream in = new FileInputStream(file)) {
            sha256 = DigestUtils.sha256Hex(in);
        }

        getLogger().info("Uploading asset {}", filename);
        final GHAsset archiveAsset = release.uploadAsset(file, mimeType);
        release.uploadAsset(filename+".txt", new ByteArrayInputStream(sha256.getBytes(StandardCharsets.UTF_8)), "text/plain");

        return new Asset(archiveAsset.getBrowserDownloadUrl(), sha256);
    }

}
