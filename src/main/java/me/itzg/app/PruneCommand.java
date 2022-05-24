package me.itzg.app;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Callable;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
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
  List<String> branchesToKeep;

  @Option(names = "--force-prune-unmerged", defaultValue = "true", negatable = true,
    description = "When set, untracked branches will be pruned even if git thinks they are not merged to master"
  )
  boolean forceDelete;

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
    try (Repository repo = new FileRepositoryBuilder()
        .setWorkTree(new File("."))
        .build()) {

      try (Git git = new Git(repo)) {
          prune(repo, git);
        return ExitCode.OK;
      }
    } catch (IOException | GitAPIException e) {
      log.warn("Failed to process current repository", e);
      return ExitCode.SOFTWARE;
    }
  }

  private void prune(Repository repo, Git git) throws GitAPIException, IOException {

    int keepCount = 0;
    int deletedCount = 0;
    final List<Ref> branches = git.branchList().call();
    for (Ref branch : branches) {
      final String shortBranchName = Repository.shortenRefName(branch.getName());
      if (!processBranch(repo, git, branch)) {
        log.info("Keeping {}", shortBranchName);
        ++keepCount;
      }
      else {
        log.info("{}DELETED {}", dryRun?"DRY RUN: ":"", shortBranchName);
        ++deletedCount;
      }
    }

    log.info("Deleted {} branch{} and kept {} branch{}",
        deletedCount, pluralSuffix(deletedCount),
        keepCount, pluralSuffix(keepCount));
  }

  private static String pluralSuffix(int count) {
    return count == 1 ? "" : "es";
  }

  /**
   * @return true if pruned
   */
  private boolean processBranch(Repository repo, Git git, Ref branch) throws IOException, GitAPIException {
    final String currentBranchName = git.getRepository().getBranch();
    final String localBranchName = Repository.shortenRefName(branch.getName());
    if (currentBranchName.equals(localBranchName)
        || branchesToKeep.contains(localBranchName)) {
      return false;
    }

    // Via a merge commit?
    if (isMergeCommitted(repo, branch)) {
      deleteBranch(git, branch);
      return true;
    }

    // Via a squash-and-merge?
    if (isContentMerged(repo, git, branch)) {
      deleteBranch(git, branch);
      return true;
    }

    return false;
  }

  private boolean isMergeCommitted(Repository repo, Ref branch) throws IOException {
    try (RevWalk walk = new RevWalk(repo)) {
      return walk.isMergedInto(
          walk.parseCommit(branch.getObjectId()),
          walk.parseCommit(repo.resolve(Constants.HEAD))
      );
    }
  }

  private boolean isContentMerged(Repository repo, Git git, Ref branch) throws IOException, GitAPIException {
    final RevCommit mergeBase = findMergeBase(repo, branch);

    final ObjectReader repoObjectReader = repo.newObjectReader();
    try (RevWalk walk = new RevWalk(repo)) {
      walk.markStart(walk.parseCommit(repo.resolve(Constants.HEAD)));
      walk.markUninteresting(walk.parseCommit(mergeBase));
      walk.setRetainBody(false);
      walk.sort(RevSort.REVERSE);

      for (final RevCommit revCommit : walk) {
        if (isMerged(git, branch, repoObjectReader, walk, revCommit)) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean isMerged(Git git, Ref branch, ObjectReader repoObjectReader, RevWalk walk, RevCommit commitToCompare)
      throws IOException, GitAPIException {
    final CanonicalTreeParser headTree = new CanonicalTreeParser();
    headTree.reset(repoObjectReader, walk.parseTree(commitToCompare));
    final CanonicalTreeParser branchTree = new CanonicalTreeParser();
    branchTree.reset(repoObjectReader, walk.parseTree(branch.getObjectId()));

    final List<DiffEntry> diffs = git.diff()
        .setNewTree(headTree)
        .setOldTree(branchTree)
        .setShowNameAndStatusOnly(true)
        .call();

    return diffs.isEmpty();
  }

  private static RevCommit findMergeBase(Repository repo, Ref branch) throws IOException {
    final RevWalk revWalk = new RevWalk(repo);
    revWalk.setRevFilter(RevFilter.MERGE_BASE);
    revWalk.markStart(repo.parseCommit(branch.getObjectId()));
    revWalk.markStart(repo.parseCommit(repo.resolve(Constants.HEAD)));
    return revWalk.next();
  }

  private void deleteBranch(Git git, Ref branch) {
    final String shortBranchName = Repository.shortenRefName(branch.getName());
    if (!dryRun) {
      try {
          git.branchDelete()
              .setBranchNames(branch.getName())
              .setForce(forceDelete)
              .call();
      } catch (GitAPIException e) {
          log.warn("Failed to delete branch '{}' since it has not been merged yet", shortBranchName);
      }
    }
  }

}
