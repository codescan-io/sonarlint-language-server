/*
 * SonarLint Language Server
 * Copyright (C) 2009-2022 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonarsource.sonarlint.ls;

import com.google.gson.JsonPrimitive;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.eclipse.lsp4j.Diagnostic;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingManager;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingWrapper;
import org.sonarsource.sonarlint.ls.connected.ServerIssueTrackerWrapper;
import org.sonarsource.sonarlint.ls.connected.TaintVulnerabilitiesCache;
import org.sonarsource.sonarlint.ls.file.VersionnedOpenFile;
import org.sonarsource.sonarlint.ls.log.LanguageClientLogger;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static org.sonarsource.sonarlint.ls.util.Utils.pluralize;

public class IssuesCache {

  private final DiagnosticPublisher diagnosticPublisher;
  private final TaintVulnerabilitiesCache taintVulnerabilitiesCache;
  private final ProjectBindingManager bindingManager;
  private final LanguageClientLogger lsLogOutput;

  private final Map<URI, Map<String, VersionnedIssue>> issuesPerIdPerFileURI = new ConcurrentHashMap<>();
  private final Map<URI, Map<String, VersionnedIssue>> inProgressAnalysisIssuesPerIdPerFileURI = new ConcurrentHashMap<>();
  private final Map<URI, CompletableFuture<Void>> updateIssuesTasks = new ConcurrentHashMap<>();

  public IssuesCache(DiagnosticPublisher diagnosticPublisher, TaintVulnerabilitiesCache taintVulnerabilitiesCache, ProjectBindingManager bindingManager,
    LanguageClientLogger lsLogOutput) {
    this.diagnosticPublisher = diagnosticPublisher;
    this.taintVulnerabilitiesCache = taintVulnerabilitiesCache;
    this.bindingManager = bindingManager;
    this.lsLogOutput = lsLogOutput;
  }

  public void clear(URI fileUri) {
    issuesPerIdPerFileURI.remove(fileUri);
    inProgressAnalysisIssuesPerIdPerFileURI.remove(fileUri);
    updateIssuesTasks.remove(fileUri);
    diagnosticPublisher.publishDiagnostics(fileUri, Map.of(), List.of());
  }

  public void analysisStarted(VersionnedOpenFile versionnedOpenFile) {
    inProgressAnalysisIssuesPerIdPerFileURI.remove(versionnedOpenFile.getUri());
  }

  public void reportIssue(VersionnedOpenFile versionnedOpenFile, Issue issue) {
    URI fileUri = versionnedOpenFile.getUri();
    inProgressAnalysisIssuesPerIdPerFileURI.computeIfAbsent(fileUri, u -> new HashMap<>()).put(UUID.randomUUID().toString(),
      new VersionnedIssue(issue, versionnedOpenFile.getVersion()));
    diagnosticPublisher.publishDiagnostics(fileUri, get(fileUri), taintVulnerabilitiesCache.get(fileUri));
  }

  public void reportIssueConnected(VersionnedOpenFile versionnedOpenFile, Issue issue, ServerIssueTrackerWrapper serverIssueTracker) {
    URI fileUri = versionnedOpenFile.getUri();
    Map<String, VersionnedIssue> issuesPerUuid = inProgressAnalysisIssuesPerIdPerFileURI.computeIfAbsent(fileUri, u -> new HashMap<>());
    issuesPerUuid.put(UUID.randomUUID().toString(), new VersionnedIssue(issue, versionnedOpenFile.getVersion()));

    List<Issue> issuesToTrack = issuesPerUuid.values().stream().map(vi -> vi.getIssue()).collect(toList());
    serverIssueTracker.matchAndTrack(versionnedOpenFile.getRelativePath(), issuesToTrack, issueListener);

    diagnosticPublisher.publishDiagnostics(fileUri, get(fileUri), taintVulnerabilitiesCache.get(fileUri));
  }

  public int count(URI f) {
    return get(f).size();
  }

  public void analysisFailed(VersionnedOpenFile versionnedOpenFile) {
    // Keep issues of the previous analysis
    inProgressAnalysisIssuesPerIdPerFileURI.remove(versionnedOpenFile.getUri());
  }

  public void analysisSucceeded(VersionnedOpenFile versionnedOpenFile) {
    URI fileUri = versionnedOpenFile.getUri();
    updateIssuesTasks.getOrDefault(fileUri, CompletableFuture.completedFuture(null)).join();
    // Swap issues
    var newIssues = inProgressAnalysisIssuesPerIdPerFileURI.remove(fileUri);
    if (newIssues != null) {
      issuesPerIdPerFileURI.put(fileUri, newIssues);
    } else {
      issuesPerIdPerFileURI.remove(fileUri);
    }
    diagnosticPublisher.publishDiagnostics(fileUri, get(fileUri), taintVulnerabilitiesCache.get(fileUri));
  }

  public Optional<VersionnedIssue> getIssueForDiagnostic(URI fileUri, Diagnostic d) {
    var issuesForFile = get(fileUri);
    return Optional.ofNullable(d.getData())
      .map(JsonPrimitive.class::cast)
      .map(JsonPrimitive::getAsString)
      .map(issuesForFile::get)
      .filter(Objects::nonNull);
  }

  public static class VersionnedIssue {
    private final Issue issue;
    private final int documentVersion;

    public VersionnedIssue(Issue issue, int documentVersion) {
      this.issue = issue;
      this.documentVersion = documentVersion;
    }

    public Issue getIssue() {
      return issue;
    }

    public int getDocumentVersion() {
      return documentVersion;
    }
  }

  private Map<String, VersionnedIssue> get(URI fileUri) {
    return inProgressAnalysisIssuesPerIdPerFileURI.getOrDefault(fileUri, issuesPerIdPerFileURI.getOrDefault(fileUri, Map.of()));
  }

  public void scheduleUpdateOfServerIssues(Map<URI, VersionnedOpenFile> filesToAnalyze, ProjectBindingWrapper bindingWrapper) {
    filesToAnalyze.forEach((fileUri, openFile) -> {
      // Don't queue a new update of server issues if one is still running
      if (updateIssuesTasks.getOrDefault(fileUri, CompletableFuture.completedFuture(null)).isDone()) {
        var relativeFilePath = openFile.getRelativePath();
        updateIssuesTasks.put(fileUri, bindingManager.updateServerIssues(bindingWrapper, relativeFilePath).thenAccept(serverIssues -> {
          taintVulnerabilitiesCache.reload(fileUri, serverIssues);
          long foundVulnerabilities = taintVulnerabilitiesCache.get(fileUri).size();
          if (foundVulnerabilities > 0) {
            lsLogOutput
              .info(format("Fetched %s taint %s for %s", foundVulnerabilities, pluralize(foundVulnerabilities, "vulnerability", "vulnerabilities"), fileUri));
          }
        }));
      }
    });
  }

}
