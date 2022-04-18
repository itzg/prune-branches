package me.itzg.app;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand.ListMode;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.NotMergedException;
import org.eclipse.jgit.lib.BranchConfig;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.transport.sshd.SshdSessionFactory;
import org.eclipse.jgit.transport.sshd.SshdSessionFactoryBuilder;
import org.eclipse.jgit.util.FS;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Option;

@Slf4j
@Command(
    description = "Prunes local branches that no longer point to existing remote branches",
    mixinStandardHelpOptions = true
)
public class PruneCommand implements Callable<Integer> {

  public static void main(String[] args) {
    System.exit(
        new CommandLine(new PruneCommand()).execute(args)
    );
  }

  @Option(names = "--always-keep", paramLabel = "BRANCH", defaultValue = "master",
    description = "The names of branches to keep from pruning, such as master."
        + " The current branch is always kept regardless of this option."
  )
  List<String> keepBranchNames;

  @Option(names = "--force-prune-unmerged", defaultValue = "true", negatable = true,
    description = "When set, untracked branches will be pruned even if git thinks they are not merged to master"
  )
  boolean forceDelete;

  @Option(names = "--ssh-directory",
    description = "Location of .ssh directory that contains ssh private key file."
        + " Default is ~/.ssh"
  )
  File sshDirectory;

  @Option(names = "--dry-run",
    description = "Just output branches that would have been deleted"
  )
  boolean dryRun;

  @SuppressWarnings("unused")
  @Option(names = "--debug", description = "Enable debug logs")
  void setDebug(boolean value) {
    ((Logger) LoggerFactory.getLogger("me.itzg.app")).setLevel(value ? Level.DEBUG : Level.INFO);
  }

  @Override
  public Integer call() {
    discoverSystemConfig();

    try (Repository repo = new FileRepositoryBuilder()
        .setWorkTree(new File("."))
        .build()) {

      final File homeDirectory = FS.detect().userHome();
      if (sshDirectory == null) {
        sshDirectory = new File(homeDirectory, ".ssh");
      }
      log.debug("Using {} for ssh directory", sshDirectory);

      final SshdSessionFactory sessionFactory = new SshdSessionFactoryBuilder()
          .setHomeDirectory(homeDirectory)
          .setSshDirectory(sshDirectory)
          .build(null);
      SshSessionFactory.setInstance(sessionFactory);

      try (Git git = new Git(repo)) {
        CredentialsProvider credentialsProvider =
            loadCredentialProvider(git);
        prune(repo, git, credentialsProvider);
        return ExitCode.OK;
      }
    } catch (IOException | GitAPIException e) {
      log.warn("Failed to process current repository", e);
      return ExitCode.SOFTWARE;
    }
  }

  private void discoverSystemConfig() {
    final File osxSystemConfig = new File("/Library/Developer/CommandLineTools/usr/share/git-core/gitconfig");

    if (osxSystemConfig.exists()) {
      FS.DETECTED.setGitSystemConfig(osxSystemConfig);
    }
  }

  private CredentialsProvider loadCredentialProvider(Git git) {
    final String helper = git.getRepository().getConfig()
        .getString("credential", null, "helper");
    if (helper != null) {
      return new CredentialHelperProvider(helper);
    }
    return null;
  }

  private void prune(Repository repo, Git git,
      CredentialsProvider credentialsProvider) throws GitAPIException, IOException {
    final String currentBranchName = git.getRepository().getBranch();

    final Set<String> remoteRefNames = fetchRemoteRefNames(repo, git, credentialsProvider);

    int keepCount = 0;
    final List<Ref> branches = git.branchList().call();
    for (Ref branch : branches) {
      final String localBranchName = Repository.shortenRefName(branch.getName());
      if (currentBranchName.equals(localBranchName)
          || keepBranchNames.contains(localBranchName)) {
        continue;
      }

      final BranchConfig branchConfig = new BranchConfig(
          repo.getConfig(),
          localBranchName
      );
      final String remoteBranch = branchConfig.getRemoteTrackingBranch();

      if (remoteBranch == null ||
          !remoteRefNames.contains(remoteBranch)) {

        log.info("Deleting local branch {}", localBranchName);

        if (!dryRun) {
          try {
            git.branchDelete()
                .setBranchNames(branch.getName())
                .setForce(forceDelete)
                .call();
          } catch (NotMergedException e) {
            log.warn("Failed to delete branch '{}' since it has not been merged yet", localBranchName);
          }
        }
      } else {
        log.debug("Keeping local branch {}", localBranchName);
        ++keepCount;
      }
    }

    log.info("Kept {} branch{}", keepCount, keepCount == 1 ? "":"es");
  }

  private Set<String> fetchRemoteRefNames(Repository repo, Git git, CredentialsProvider credentialsProvider)
      throws GitAPIException {
    final Set<String> remoteRefNames = new HashSet<>();

    for (String remoteName : repo.getRemoteNames()) {
      log.debug("Fetching remote {}", remoteName);
      try {
        git.fetch()
            .setCredentialsProvider(credentialsProvider)
            .setRemote(remoteName)
            .setRemoveDeletedRefs(true)
            .call();
      } catch (InvalidRemoteException e) {
        log.warn("Remote {} is not valid, considering it removed", remoteName);
        continue;
      }

      for (Ref remoteRef : git.branchList()
          .setListMode(ListMode.REMOTE)
          .call()) {
        remoteRefNames.add(remoteRef.getName());
      }
    }
    return remoteRefNames;
  }

}
