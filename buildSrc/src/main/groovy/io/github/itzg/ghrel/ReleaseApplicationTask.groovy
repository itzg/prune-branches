package io.github.itzg.ghrel

import io.github.itzg.ghrel.Assets.Asset
import org.apache.commons.codec.digest.DigestUtils
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.kohsuke.github.*

import javax.inject.Inject
import java.nio.charset.StandardCharsets
import java.nio.file.Files

abstract class ReleaseApplicationTask extends DefaultTask {

    private GithubReleaserExtension extension;

    @InputFile
    abstract RegularFileProperty getApplicationTar();

    @InputFile
    abstract RegularFileProperty getApplicationZip();

    @Internal
    abstract Property<Assets> getAssets();

    @Inject
    ReleaseApplicationTask() {
    }

    void apply(GithubReleaserExtension extension) {
        this.extension = extension;
    }

    @TaskAction
    void releaseAndPublish() {
        try {
            final GitHub gitHub = new GitHubBuilder()
                .withEndpoint(extension.githubApiUrl.get())
                .withOAuthToken(extension.githubToken.get())
                .build();

            GHRelease release = getOurRelease(gitHub);

            final Assets assets = addArchives(release);
            getAssets().set(assets);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private GHRelease getOurRelease(GitHub gitHub) {
        final String repoName = extension.githubRepository.get();
        final GHRepository repository;
        try {
            repository = gitHub.getRepository(repoName);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to lookup repository given "+repoName, e);
        }


        final String tagOrReleaseName = extension.releaseName.get();
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

    private static GHRelease findReleaseByName(GHRepository repository, String tagOrReleaseName) throws IOException {
        final GHRelease release = repository.getReleaseByTagName(tagOrReleaseName);
        if (release != null) {
            return release;
        }

        for (final GHRelease eachRelease : repository.listReleases()) {
            if (eachRelease.name.equals(tagOrReleaseName)) {
                return eachRelease;
            }
        }
        return null;
    }

    private Assets addArchives(GHRelease release) throws IOException {
        final Asset zipAsset = uploadAsset(release, applicationZip);
        final Asset tarAsset = uploadAsset(release, applicationTar);

        return new Assets(zipAsset, tarAsset);
    }

    private Asset uploadAsset(GHRelease release, RegularFileProperty archive) throws IOException {
        final File file = archive.asFile.get();
        final String mimeType = Files.probeContentType(file.toPath());
        final String filename = file.name;

        for (final GHAsset existing : release.listAssets()) {
            if (existing.name.startsWith(filename)) {
                logger.info("Replacing existing asset {}", existing.name);
                existing.delete();
            }
        }

        final String sha256 = file.withInputStream {DigestUtils.sha256Hex(it) }

        logger.info("Uploading asset {}", filename);
        final GHAsset archiveAsset = release.uploadAsset(file, mimeType);
        release.uploadAsset("${filename}.sha256", new ByteArrayInputStream(sha256.getBytes(StandardCharsets.UTF_8)), "text/plain");

        return new Asset(archiveAsset.browserDownloadUrl, sha256);
    }

}
