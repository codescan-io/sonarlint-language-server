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

import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.client.api.connected.ServerIssue;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.ls.IssuesCache.VersionnedIssue;
import org.sonarsource.sonarlint.ls.util.Utils;

import static java.util.stream.Collectors.toList;
import static org.sonarsource.sonarlint.ls.util.Utils.buildMessageWithPluralizedSuffix;

public class DiagnosticPublisher {

  static final String SONARLINT_SOURCE = "sonarlint";
  static final String SONARQUBE_TAINT_SOURCE = "SonarQube Taint Analyzer";

  public static final String ITEM_LOCATION = "location";
  public static final String ITEM_FLOW = "flow";

  private final SonarLintExtendedLanguageClient client;
  private boolean firstSecretIssueDetected;

  public DiagnosticPublisher(SonarLintExtendedLanguageClient client) {
    this.client = client;
  }

  public void initialize(boolean firstSecretDetected) {
    this.firstSecretIssueDetected = firstSecretDetected;
  }

  public void publishDiagnostics(URI f, Map<String, VersionnedIssue> localIssues, List<ServerIssue> taintVulnerabilities) {
    client.publishDiagnostics(createPublishDiagnosticsParams(f, localIssues, taintVulnerabilities));
  }

  private PublishDiagnosticsParams createPublishDiagnosticsParams(URI fileUri, Map<String, VersionnedIssue> localIssues, List<ServerIssue> taintVulnerabilities) {
    var p = new PublishDiagnosticsParams();

    if (!firstSecretIssueDetected && localIssues.values().stream().anyMatch(v -> v.getIssue().getRuleKey().startsWith(Language.SECRETS.getPluginKey()))) {
      client.showFirstSecretDetectionNotification();
      firstSecretIssueDetected = true;
    }

    var localDiagnostics = localIssues.entrySet()
      .stream()
      .map(DiagnosticPublisher::convert);
    var taintDiagnostics = asDiagnostics(fileUri, taintVulnerabilities);

    p.setDiagnostics(Stream.concat(localDiagnostics, taintDiagnostics)
      .sorted(DiagnosticPublisher.byLineNumber())
      .collect(toList()));
    p.setUri(fileUri.toString());

    return p;
  }

  public Stream<Diagnostic> asDiagnostics(URI fileUri, List<ServerIssue> taintVulnerabilities) {
    return taintVulnerabilities
      .stream()
      .map(i -> convert(i));
  }

  static Diagnostic convert(Map.Entry<String, VersionnedIssue> entry) {
    var issue = entry.getValue().getIssue();
    var range = Utils.convert(issue);

    var diagnostic = new Diagnostic();
    diagnostic.setSeverity(severity(issue.getSeverity()));
    diagnostic.setRange(range);
    diagnostic.setCode(issue.getRuleKey());
    diagnostic.setMessage(message(issue));
    diagnostic.setSource(SONARLINT_SOURCE);
    diagnostic.setData(entry.getKey());

    return diagnostic;
  }

  static Diagnostic convert(ServerIssue taintIssue) {
    var range = Utils.convert(taintIssue);

    var diagnostic = new Diagnostic();
    diagnostic.setSeverity(severity(taintIssue.severity()));
    diagnostic.setRange(range);
    diagnostic.setCode(taintIssue.ruleKey());
    diagnostic.setMessage(message(taintIssue));
    diagnostic.setSource(SONARQUBE_TAINT_SOURCE);
    diagnostic.setData(taintIssue.key());

    return diagnostic;
  }

  public static DiagnosticSeverity severity(String sonarSeverity) {
    switch (sonarSeverity.toUpperCase(Locale.ENGLISH)) {
      case "BLOCKER":
      case "CRITICAL":
      case "MAJOR":
        return DiagnosticSeverity.Warning;
      case "MINOR":
        return DiagnosticSeverity.Information;
      case "INFO":
      default:
        return DiagnosticSeverity.Hint;
    }
  }

  static String message(ServerIssue issue) {
    int nbFlows = issue.getFlows().size();
    String message = issue.getMessage();
    boolean hasOneLocationPerFlow = issue.getFlows().stream().allMatch(f -> f.locations().size() == 1);
    int firstFlowLocationCount = nbFlows == 0 ? 0 : issue.getFlows().get(0).locations().size();
    return message(nbFlows, message, hasOneLocationPerFlow, firstFlowLocationCount);
  }

  static String message(Issue issue) {
    int nbFlows = issue.flows().size();
    String message = issue.getMessage();
    boolean hasOneLocationPerFlow = issue.flows().stream().allMatch(f -> f.locations().size() == 1);
    int firstFlowLocationCount = nbFlows == 0 ? 0 : issue.flows().get(0).locations().size();
    return message(nbFlows, message, hasOneLocationPerFlow, firstFlowLocationCount);
  }

  private static String message(int nbFlows, String message, boolean hasOneLocationPerFlow, int firstFlowLocationCount) {
    if (nbFlows == 0) {
      return message;
    } else if (nbFlows == 1) {
      return buildMessageWithPluralizedSuffix(message, firstFlowLocationCount, ITEM_LOCATION);
    } else if (hasOneLocationPerFlow) {
      int nbSecondaryLocations = nbFlows;
      return buildMessageWithPluralizedSuffix(message, nbSecondaryLocations, ITEM_LOCATION);
    } else {
      return buildMessageWithPluralizedSuffix(message, nbFlows, ITEM_FLOW);
    }
  }

  private static Comparator<? super Diagnostic> byLineNumber() {
    return Comparator.comparing((Diagnostic d) -> d.getRange().getStart().getLine())
      .thenComparing(Diagnostic::getMessage);
  }

}
