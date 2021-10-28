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
import org.eclipse.jgit.api.errors.NotMergedException;
import org.eclipse.jgit.lib.BranchConfig;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
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
    description = "The names of branches to keep from pruning, such as master"
  )
  List<String> keepBranchNames;

  @Option(names = "--force-prune-unmerged", defaultValue = "true", negatable = true,
    description = "When set, untracked branches will be pruned even if git thinks they are not merged to master"
  )
  boolean forceDelete;

  @SuppressWarnings("unused")
  @Option(names = "--debug", description = "Enable debug logs")
  void setDebug(boolean value) {
    ((Logger) LoggerFactory.getLogger("me.itzg.app")).setLevel(value ? Level.DEBUG : Level.INFO);
  }

  @Override
  public Integer call() {
    try (Repository repo = new FileRepositoryBuilder()
        .setWorkTree(new File("."))
        .build()) {

      if (!"master".equals(repo.getBranch())) {
        log.error("Current branch must be master");
        System.exit(1);
      }

      final File homeDirectory = FS.detect().userHome();
      final SshdSessionFactory sessionFactory = new SshdSessionFactoryBuilder()
          .setHomeDirectory(homeDirectory)
          .setSshDirectory(new File(homeDirectory, ".ssh"))
          .build(null);
      SshSessionFactory.setInstance(sessionFactory);

      try (Git git = new Git(repo)) {
        prune(repo, git);
        return ExitCode.OK;
      }
    } catch (IOException | GitAPIException e) {
      log.warn("Failed to process current repository", e);
      return ExitCode.SOFTWARE;
    }
  }

  private void prune(Repository repo, Git git) throws GitAPIException {
    final Set<String> remoteRefNames = new HashSet<>();

    for (String remoteName : repo.getRemoteNames()) {
      log.debug("Fetching remote {}", remoteName);
      git.fetch()
          .setRemote(remoteName)
          .setRemoveDeletedRefs(true)
          .call();

      for (Ref remoteRef : git.branchList()
          .setListMode(ListMode.REMOTE)
          .call()) {
        remoteRefNames.add(remoteRef.getName());
      }
    }

    final List<Ref> branches = git.branchList().call();
    for (Ref branch : branches) {
      final String localBranchName = Repository.shortenRefName(branch.getName());
      if (keepBranchNames.contains(localBranchName)) {
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

        try {
          git.branchDelete()
              .setBranchNames(branch.getName())
              .setForce(forceDelete)
              .call();
        } catch (NotMergedException e) {
          log.warn("Failed to delete branch '{}' since it has not been merged yet", localBranchName);
        }
      } else {
        log.debug("Keeping local branch {}", localBranchName);
      }
    }
  }

}
