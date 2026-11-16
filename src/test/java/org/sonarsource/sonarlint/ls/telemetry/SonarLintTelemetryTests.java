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

import java.util.Collections;
import java.util.HashMap;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.ls.NodeJsRuntime;
import org.sonarsource.sonarlint.ls.backend.BackendServiceFacade;
import org.sonarsource.sonarlint.ls.connected.ProjectBindingManager;
import org.sonarsource.sonarlint.ls.settings.SettingsManager;
import org.sonarsource.sonarlint.ls.settings.WorkspaceSettings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class SonarLintTelemetryTests {
  private final SettingsManager settingsManager = mock(SettingsManager.class);
  private final ProjectBindingManager bindingManager = mock(ProjectBindingManager.class);
  private final NodeJsRuntime nodeJsRuntime = mock(NodeJsRuntime.class);
  private final BackendServiceFacade backendServiceFacade = mock(BackendServiceFacade.class);
  private final SonarLintTelemetry telemetry = new SonarLintTelemetry(settingsManager, bindingManager, nodeJsRuntime, backendServiceFacade);

  @Test
  void stays_off_after_initialize() {
    telemetry.initialize(new TelemetryInitParams("vscode", "storage", "CodeScan", "1.0", "1.90", "darwin", "arm64", new HashMap<>()));
    assertThat(telemetry.enabled()).isFalse();
  }

  @Test
  void the_setting_cannot_turn_it_on() {
    telemetry.onChange(null, settingsWithDisableTelemetry(false));
    assertThat(telemetry.enabled()).isFalse();
  }

  @Test
  void never_touches_the_http_client_or_settings() {
    telemetry.initialize(new TelemetryInitParams("vscode", "storage", "CodeScan", "1.0", "1.90", "darwin", "arm64", new HashMap<>()));
    telemetry.onChange(null, settingsWithDisableTelemetry(false));
    telemetry.analysisDoneOnMultipleFiles();
    telemetry.analysisDoneOnSingleLanguage(Language.JAVA, 1000);
    telemetry.addReportedRules(Collections.singleton("repo:key"));
    telemetry.devNotificationsReceived("category");
    telemetry.devNotificationsClicked("event");
    telemetry.showHotspotRequestReceived();
    telemetry.taintVulnerabilitiesInvestigatedLocally();
    telemetry.taintVulnerabilitiesInvestigatedRemotely();
    telemetry.addQuickFixAppliedForRule("repo:key");
    telemetry.helpAndFeedbackLinkClicked("docs");
    telemetry.stop();

    verifyNoInteractions(backendServiceFacade, settingsManager, bindingManager, nodeJsRuntime);
    assertThat(telemetry.enabled()).isFalse();
  }

  private static WorkspaceSettings settingsWithDisableTelemetry(boolean disableTelemetry) {
    return new WorkspaceSettings(disableTelemetry, Collections.emptyMap(), Collections.emptyList(), Collections.emptyList(), Collections.emptyMap(), false, false, null);
  }
}
