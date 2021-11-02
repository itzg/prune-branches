package me.itzg.app;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.errors.UnsupportedCredentialItem;
import org.eclipse.jgit.transport.CredentialItem;
import org.eclipse.jgit.transport.CredentialItem.Password;
import org.eclipse.jgit.transport.CredentialItem.Username;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.URIish;

@Slf4j
public class CredentialHelperProvider extends CredentialsProvider {
  private final String credentialHelper;

  public CredentialHelperProvider(String credentialHelper) {
    this.credentialHelper = credentialHelper;
  }

  @Override
  public boolean isInteractive() {
    return true;
  }

  @Override
  public boolean supports(CredentialItem... items) {
    return Arrays.stream(items)
        .allMatch(
            item -> item instanceof Username ||
                item instanceof Password
        );
  }

  @Override
  public boolean get(URIish uri, CredentialItem... items) throws UnsupportedCredentialItem {
    final String scheme = uri.getScheme();
    final String host = uri.getHost();
    log.debug("Using credential helper {} to lookup scheme={} host={}",
        credentialHelper, scheme, host);

    try {
      final Process gitProc = new ProcessBuilder("git", "credential-" + credentialHelper, "get")
          .start();

      final PrintStream stdin = new PrintStream(gitProc.getOutputStream());
      final BufferedReader stdout = new BufferedReader(new InputStreamReader(gitProc.getInputStream()));

      stdin.println("protocol="+scheme);
      stdin.println("host="+host);
      stdin.close();

      Map<String, String> results = new HashMap<>();
      String line;
      while ((line = stdout.readLine()) != null) {
        final String[] parts = line.split("=", 2);
        if (parts.length == 2) {
          results.put(parts[0], parts[1]);
        }
      }

      final int statusCode = gitProc.waitFor();

      if (statusCode != 0) {
        log.warn("git credential helper {} failed: {}", credentialHelper, statusCode);
        return false;
      }

      for (CredentialItem item : items) {
        if (item instanceof CredentialItem.Username) {
          ((Username) item).setValue(results.get("username"));
        } else if (item instanceof CredentialItem.Password) {
          final String passwordStr = results.get("password");
          if (passwordStr != null) {
            ((Password) item).setValue(passwordStr.toCharArray());
          }
        }
      }

      return true;

    } catch (IOException e) {
      throw new IllegalStateException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }
}
