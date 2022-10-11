package io.github.itzg.ghrel

import groovy.json.JsonBuilder
import io.github.itzg.ghrel.Assets.Asset
import org.apache.commons.text.CaseUtils
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.kohsuke.github.GHContent
import org.kohsuke.github.GHRepository
import org.kohsuke.github.GitHub
import org.kohsuke.github.GitHubBuilder

abstract class PublishApplicationTask extends DefaultTask {

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
    void publish() {
        final GitHub gitHub;
        try {
            gitHub = new GitHubBuilder()
                .withEndpoint(extension.githubApiUrl.get())
                .withOAuthToken(
                    extension.publish.githubPublishToken
                        .orElse(extension.githubToken)
                        .get()
                )
                .build();
        } catch (IOException e) {
            throw new RuntimeException("Allocating GitHub client for publishing", e);
        }

        if (extension.publish.scoop.repository.present) {
            publishToScoopBucket(gitHub);
        }

        if (extension.publish.homebrew.repository.present) {
            publishToBrewTap(gitHub)
        }
    }

    private void publishToScoopBucket(GitHub gitHub) {
        final PublishProperties publishProps = extension.publish;
        final ScoopProperties scoopProps = publishProps.scoop;

        final String name = applicationName.get();
        final String bucketDirectory = scoopProps.bucketDirectory.orNull;
        final String contentPath = bucketDirectory != null ? "${bucketDirectory}/${name}.json" : "${name}.json"
        final Asset zipFile = assets.get().zipFile;
        final String version = applicationVersion.get();
        def contentBuilder = new JsonBuilder([
            'version': version,
            'url': zipFile.downloadUrl,
            'hash': "sha256:${zipFile.sha256}",
            'bin': "bin/${name}.bat",
            'extract_dir': zipTopDirectory.get(),
            'suggest': [
                'JRE': ['java/temurin-lts-jre']
            ]
        ])

        pushFileToRepo(
            gitHub, scoopProps.repository, scoopProps.branch, 'Scoop bucket',
            contentPath, contentBuilder.toPrettyString()
        )
    }

    private void publishToBrewTap(GitHub gitHub) {
        def homebrew = extension.publish.homebrew
        def name = applicationName.get()
        def formulaTemplate = """
class ${CaseUtils.toCamelCase(name, true, '-' as char)} < Formula
  desc "${project.description}"
  homepage "${extension.project.homepage.getOrElse('')}"
  url "${assets.get().tarFile.downloadUrl}"
  sha256 "${assets.get().tarFile.sha256}"
  license "${extension.project.license.getOrElse('')}"

  depends_on "${homebrew.javaDependency.get()}"

  def install
    libexec.install Dir["*"]
    bin.install_symlink "#{libexec}/bin/${name}"
  end
end
"""
        pushFileToRepo(gitHub, homebrew.repository, homebrew.branch, 'Homebrew tap',
            "Formula/${name}.rb", formulaTemplate.toString())
    }

    private void pushFileToRepo(GitHub gitHub, Property<String> repositoryName, Property<String> branch,
                                String publicationType,
                                String contentPath, String content) {
        final GHRepository repo;
        try {
            repo = gitHub.getRepository(repositoryName.get());
        } catch (IOException e) {
            throw new RuntimeException(
                "Failed accessing scoop bucket repository ${repositoryName.get()}: ${e.message}", e);
        }

        GHContent existingContent;
        try {
            existingContent = repo.getFileContent(contentPath);
        } catch (FileNotFoundException ignored) {
            existingContent = null;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try {
            logger.info("Publishing to {} {}", publicationType, repo.fullName);
            repo.createContent()
                .branch(
                    branch
                        .orElse(project.provider(repo::getDefaultBranch))
                        .get()
                )
                .sha(existingContent != null ? existingContent.sha : null)
                .content(content)
                .path(contentPath)
                .message("${applicationName.get()}: released ${applicationVersion.get()}")
                .commit();
        } catch (IOException e) {
            throw new RuntimeException("Publishing manifest update", e);
        }
    }
}
