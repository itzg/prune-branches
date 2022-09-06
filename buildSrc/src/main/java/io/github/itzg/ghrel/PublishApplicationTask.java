package io.github.itzg.ghrel;

import io.github.itzg.ghrel.Assets.Asset;
import java.io.FileNotFoundException;
import java.io.IOException;
import org.gradle.api.DefaultTask;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;
import org.json.JSONArray;
import org.json.JSONObject;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;

public abstract class PublishApplicationTask extends DefaultTask {

    @Internal
    abstract Property<Assets> getAssets();

    @Internal
    abstract Property<String> getApplicationName();

    @Internal
    abstract Property<String> getApplicationVersion();

    @Internal
    abstract Property<String> getZipTopDirectory();

    private GithubReleaserExtension extension;

    void apply(GithubReleaserExtension extension) {

        this.extension = extension;
    }

    @TaskAction
    public void publish() {
        final GitHub gitHub;
        try {
            gitHub = new GitHubBuilder()
                .withEndpoint(extension.getGithubApiUrl().get())
                .withOAuthToken(
                    extension.getPublish().getGithubPublishToken()
                        .orElse(extension.getGithubToken())
                        .get()
                )
                .build();
        } catch (IOException e) {
            throw new RuntimeException("Allocating GitHub client for publishing", e);
        }

        if (extension.getPublish().getScoop().getRepository().isPresent()) {
            publishToScoopBucket(gitHub);
        }
    }

    private void publishToScoopBucket(GitHub gitHub) {
        final PublishProperties publishProps = extension.getPublish();
        final ScoopProperties scoopProps = publishProps.getScoop();

        final GHRepository repo;
        final String scoopBucketRepo = scoopProps.getRepository().get();
        try {
            repo = gitHub.getRepository(scoopBucketRepo);
        } catch (IOException e) {
            throw new RuntimeException("Failed to locate scoop bucket repository "+ scoopBucketRepo, e);
        }

        final String name = getApplicationName().get();
        final String manifestName = name + ".json";
        final String bucketDirectory = scoopProps.getBucketDirectory().getOrNull();
        final String contentPath = (bucketDirectory != null ? bucketDirectory+"/" : "") + manifestName;
        GHContent existingContent;
        try {
            existingContent = repo.getFileContent(contentPath);
        } catch (FileNotFoundException e) {
            existingContent = null;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        final Asset zipFile = getAssets().get().getZipFile();
        final String version = getApplicationVersion().get();
        final String content = new JSONObject()
            .put("version", version)
            .put("url", zipFile.getDownloadUrl())
            .put("hash", "sha256:"+ zipFile.getSha256())
            .put("bin", "bin/"+ name +".bat")
            .put("extract_dir", getZipTopDirectory().get())
            .put("suggest", new JSONObject()
                .put("JRE", new JSONArray()
                    .put("java/temurin-lts-jre")
                )
            )
            .toString(2);

        try {
            getLogger().info("Publishing to Scoop bucket repository {}", repo.getFullName());
            repo.createContent()
                .branch(
                    scoopProps.getBranch()
                        .orElse(getProject().provider(repo::getDefaultBranch))
                        .get()
                )
                .sha(existingContent != null ? existingContent.getSha() : null)
                .content(content)
                .path(contentPath)
                .message(name + " " + version)
                .commit();
        } catch (IOException e) {
            throw new RuntimeException("Publishing manifest update", e);
        }
    }
}
