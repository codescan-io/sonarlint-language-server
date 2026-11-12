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
package org.sonarsource.sonarlint.ls.settings;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ServerConnectionSettingsTests {

  private static final ServerConnectionSettings WITHOUT_ORG = new ServerConnectionSettings("serverId", "https://app.codescan.io", "token", null, false);
  private static final ServerConnectionSettings WITH_ORG = new ServerConnectionSettings("serverId", "https://app.codescan.io", "token", "myOrg", false);

  @Test
  void testHashCode() {
    assertThat(new ServerConnectionSettings("serverId", "https://app.codescan.io", "token", "myOrg", false)).hasSameHashCodeAs(WITH_ORG);
    assertThat(new ServerConnectionSettings("serverId", "https://app.codescan.io", "token", null, false)).hasSameHashCodeAs(WITHOUT_ORG);
  }

  @Test
  void testEquals() {
    assertThat(WITH_ORG)
      .isEqualTo(WITH_ORG)
      .isEqualTo(new ServerConnectionSettings("serverId", "https://app.codescan.io", "token", "myOrg", false))
      .isNotEqualTo(null)
      .isNotEqualTo("foo")
      .isNotEqualTo(new ServerConnectionSettings("serverId2", "https://app.codescan.io", "token", "myOrg", false))
      .isNotEqualTo(new ServerConnectionSettings("serverId", "https://other.codescan.io", "token", "myOrg", false))
      .isNotEqualTo(new ServerConnectionSettings("serverId", "https://app.codescan.io", "token2", "myOrg", false))
      .isNotEqualTo(new ServerConnectionSettings("serverId", "https://app.codescan.io", "token", "myOrg2", false))
      .isNotEqualTo(new ServerConnectionSettings("serverId", "https://app.codescan.io", "token", "myOrg2", true));
    assertThat(WITHOUT_ORG)
      .isEqualTo(new ServerConnectionSettings("serverId", "https://app.codescan.io", "token", null, false))
      .isNotEqualTo(WITH_ORG);
  }

  @Test
  void testToString() {
    assertThat(WITH_ORG).hasToString("ServerConnectionSettings[connectionId=serverId,disableNotifications=false,organizationKey=myOrg,serverUrl=https://app.codescan.io,token=*****]");
    assertThat(new ServerConnectionSettings("serverId", "https://app.codescan.io", "abcdef123456", null, false).toString())
      .contains("token=abcdef******")
      .doesNotContain("123456");
  }

  @Test
  void testMaskToken() {
    assertThat(ServerConnectionSettings.maskToken(null)).isNull();
    assertThat(ServerConnectionSettings.maskToken("")).isEmpty();
    assertThat(ServerConnectionSettings.maskToken("abcdef")).isEqualTo("******");
    assertThat(ServerConnectionSettings.maskToken("abcdefg")).isEqualTo("abcdef*");
  }

}
