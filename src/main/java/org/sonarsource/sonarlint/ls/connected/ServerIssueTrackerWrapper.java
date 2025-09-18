/*
 * SonarLint Language Server
 * Copyright (C) 2009-2023 SonarSource SA
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
package org.sonarsource.sonarlint.ls.connected;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import org.sonarsource.sonarlint.core.serverapi.util.ServerApiUtils;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Common.TextRange;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.http.HttpClient;
import org.sonarsource.sonarlint.core.issuetracking.CachingIssueTracker;
import org.sonarsource.sonarlint.core.issuetracking.InMemoryIssueTrackerCache;
import org.sonarsource.sonarlint.core.issuetracking.IssueTrackerCache;
import org.sonarsource.sonarlint.core.issuetracking.Trackable;
import org.sonarsource.sonarlint.core.serverapi.EndpointParams;
import org.sonarsource.sonarlint.core.serverconnection.ProjectBinding;
import org.sonarsource.sonarlint.core.tracking.IssueTrackable;

import static java.util.function.Predicate.not;

public class ServerIssueTrackerWrapper {

  private final ConnectedSonarLintEngine engine;
  private final EndpointParams endpointParams;
  private final ProjectBinding projectBinding;
  private final Supplier<String> getReferenceBranchNameForFolder;
  private final HttpClient httpClient;

  private final IssueTrackerCache<Issue> issueTrackerCache;
  private final IssueTrackerCache<Issue> hotspotsTrackerCache;
  private final CachingIssueTracker cachingIssueTracker;
  private final CachingIssueTracker cachingHotspotsTracker;
  private final org.sonarsource.sonarlint.core.tracking.ServerIssueTracker tracker;
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  ServerIssueTrackerWrapper(ConnectedSonarLintEngine engine, EndpointParams endpointParams,
    ProjectBinding projectBinding, Supplier<String> getReferenceBranchNameForFolder, HttpClient httpClient) {
    this.engine = engine;
    this.endpointParams = endpointParams;
    this.projectBinding = projectBinding;
    this.getReferenceBranchNameForFolder = getReferenceBranchNameForFolder;
    this.httpClient = httpClient;

    this.issueTrackerCache = new InMemoryIssueTrackerCache();
    this.hotspotsTrackerCache = new InMemoryIssueTrackerCache();
    this.cachingIssueTracker = new CachingIssueTracker(issueTrackerCache);
    this.cachingHotspotsTracker = new CachingIssueTracker(hotspotsTrackerCache);
    this.tracker = new org.sonarsource.sonarlint.core.tracking.ServerIssueTracker(cachingIssueTracker, cachingHotspotsTracker);
  }

  public void matchAndTrack(String filePath, Collection<Issue> issues, IssueListener issueListener, boolean shouldFetchServerIssues) {
    LOG.info("======Match and track, issue size: {}", issues.size());
    if (issues.isEmpty()) {
      issueTrackerCache.put(filePath, Collections.emptyList());
      return;
    }

    cachingIssueTracker.matchAndTrackAsNew(filePath, toIssueTrackables(issues));
    cachingHotspotsTracker.matchAndTrackAsNew(filePath, toHotspotTrackables(issues));

    if (shouldFetchServerIssues) {
      tracker.update(endpointParams, httpClient, engine, projectBinding,
        Collections.singleton(filePath), getReferenceBranchNameForFolder.get());
    } else {
      tracker.update(engine, projectBinding, getReferenceBranchNameForFolder.get(), Collections.singleton(filePath));
    }

    issueTrackerCache.getLiveOrFail(filePath).stream().forEach(it -> {
      LOG.info("======Tracking Issue: {}, {}, {}", it.getRuleKey(), it.getLine(), it.isResolved());
    });

    issueTrackerCache.getLiveOrFail(filePath).stream()
      .filter(not(Trackable::isResolved))
      .forEach(trackable -> issueListener.handle(new DelegatingIssue(trackable)));
    hotspotsTrackerCache.getLiveOrFail(filePath).stream()
      .filter(not(Trackable::isResolved))
      .forEach(trackable -> issueListener.handle(new DelegatingIssue(trackable)));
  }

  private static Collection<Trackable> toIssueTrackables(Collection<Issue> issues) {
    issues.forEach(it -> LOG.info("======ITT issue: {} {} {} {} {}", it.getRuleKey(), it.getTextRange().getStartLine(), it.getTextRange().getStartLineOffset(), it.getTextRange().getEndLine(), it.getTextRange().getEndLineOffset()));
    List<Trackable> itt = issues.stream()
      .filter(it -> it.getType() != RuleType.SECURITY_HOTSPOT)
      .map(ServerIssueTrackerWrapper::createIssueTrackable).collect(Collectors.toList());
    itt.forEach(it -> LOG.info("======ITT post: {} {} {} {}", it.getRuleKey(), it.getLine(), it.getTextRange().getHash(), it.getLineHash()));
    return itt;
  }

  private static IssueTrackable createIssueTrackable(Issue issue) {
    try {
      // Get file content to calculate line hash and text range hash
      var inputFile = issue.getInputFile();
      if (inputFile != null && issue.getStartLine() != null) {
        var content = inputFile.contents();
        var lines = content.split("\\r?\\n");
        var lineIndex = issue.getStartLine() - 1; // Convert to 0-based index

        if (lineIndex >= 0 && lineIndex < lines.length) {
          var lineContent = lines[lineIndex];
          
          // Calculate text range content using existing utility if issue has text range
          String textRangeContent = null;
          if (issue.getStartLineOffset() != null && issue.getEndLineOffset() != null) {
            var textRange = TextRange.newBuilder()
              .setStartLine(issue.getStartLine())
              .setStartOffset(issue.getStartLineOffset())
              .setEndLine(issue.getEndLine())
              .setEndOffset(issue.getEndLineOffset())
              .build();
            textRangeContent = ServerApiUtils.extractCodeSnippet(content, textRange);
          }

          return new IssueTrackable(issue, textRangeContent, lineContent);
        }
      }
    } catch (IOException e) {
      LOG.warn("Failed to read file content for issue tracking: {}", e.getMessage());
    }

    // Fallback to basic constructor if we can't get file content
    return new IssueTrackable(issue);
  }

  private static Collection<Trackable> toHotspotTrackables(Collection<Issue> issues) {
    return issues.stream().filter(it-> it.getType() == RuleType.SECURITY_HOTSPOT)
      .map(IssueTrackable::new).collect(Collectors.toList());
  }
}
