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
package org.sonarsource.sonarlint.ls.telemetry;

import java.util.Set;
import javax.annotation.CheckForNull;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.ls.NodeJsRuntime;
import org.sonarsource.sonarlint.ls.backend.BackendServiceFacade;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingManager;
import org.sonarsource.sonarlint.ls.settings.SettingsManager;
import org.sonarsource.sonarlint.ls.settings.WorkspaceSettings;
import org.sonarsource.sonarlint.ls.settings.WorkspaceSettingsChangeListener;

/**
 * Telemetry is always off in CodeScan. The upstream SonarLint client sends usage data to SonarSource; this class keeps the
 * same methods so callers don't change, but it never creates a sender, never schedules an upload and ignores the
 * "disable telemetry" setting, so nothing can turn it back on.
 */
public class SonarLintTelemetry implements WorkspaceSettingsChangeListener {
  /** Kept for callers and tests that still set it; it no longer changes anything. */
  public static final String DISABLE_PROPERTY_KEY = "codescan.telemetry.disabled";
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  public SonarLintTelemetry(SettingsManager settingsManager, ProjectBindingManager bindingManager, NodeJsRuntime nodeJsRuntime,
    BackendServiceFacade backendServiceFacade) {
    // Nothing to keep: telemetry is never started.
  }

  public boolean enabled() {
    return false;
  }

  public void initialize(TelemetryInitParams telemetryInitParams) {
    LOG.debug("Telemetry is disabled");
  }

  public void analysisDoneOnMultipleFiles() {
    // Telemetry is disabled
  }

  public void analysisDoneOnSingleLanguage(Language language, int analysisTimeMs) {
    // Telemetry is disabled
  }

  public void addReportedRules(Set<String> ruleKeys) {
    // Telemetry is disabled
  }

  public void devNotificationsReceived(String category) {
    // Telemetry is disabled
  }

  public void devNotificationsClicked(String eventType) {
    // Telemetry is disabled
  }

  public void showHotspotRequestReceived() {
    // Telemetry is disabled
  }

  public void taintVulnerabilitiesInvestigatedLocally() {
    // Telemetry is disabled
  }

  public void taintVulnerabilitiesInvestigatedRemotely() {
    // Telemetry is disabled
  }

  public void addQuickFixAppliedForRule(String ruleKey) {
    // Telemetry is disabled
  }

  public void helpAndFeedbackLinkClicked(String itemId) {
    // Telemetry is disabled
  }

  public void stop() {
    // Nothing was started
  }

  @Override
  public void onChange(@CheckForNull WorkspaceSettings oldValue, WorkspaceSettings newValue) {
    // The "disable telemetry" setting can't turn telemetry on.
  }
}
