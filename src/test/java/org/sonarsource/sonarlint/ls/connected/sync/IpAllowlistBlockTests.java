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
package org.sonarsource.sonarlint.ls.connected.sync;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IpAllowlistBlockTests {
  private static final String MSG = "Access to organization 'acme' is blocked by its IP allowlist. Your IP address is 203.0.113.9.";

  @Test
  void finds_the_message_deep_in_the_cause_chain() {
    var e = new RuntimeException("Failed to update", new RuntimeException("wrapper", new IllegalStateException(MSG)));
    assertThat(IpAllowlistBlock.find(e)).contains(MSG);
  }

  @Test
  void extracts_the_message_from_a_raw_json_body() {
    var e = new IllegalStateException("HTTP Connection failed (403): {\"errors\":[{\"msg\":\"" + MSG + "\"}]}");
    assertThat(IpAllowlistBlock.find(e)).contains(MSG);
  }

  @Test
  void decodes_the_escaped_quotes_the_server_sends() {
    var body = "{\"errors\":[{\"msg\":\"Access to organization \\u0027acme\\u0027 is blocked by its IP allowlist. Your IP address is 203.0.113.9.\"}]}";
    var e = new IllegalStateException("HTTP Connection failed (403): " + body);
    assertThat(IpAllowlistBlock.find(e)).contains(MSG);
  }

  @Test
  void picks_the_matching_entry_when_the_json_body_has_several_errors() {
    var e = new IllegalStateException("{\"errors\":[{\"msg\":\"Something else\"},{\"msg\":\"" + MSG + "\"}]}");
    assertThat(IpAllowlistBlock.find(e)).contains(MSG);
  }

  @Test
  void ignores_other_errors() {
    assertThat(IpAllowlistBlock.find(new IllegalStateException("Not authorized. Please check server credentials."))).isEmpty();
    assertThat(IpAllowlistBlock.find(new RuntimeException("Failed", new IllegalStateException("Error 500")))).isEmpty();
    assertThat(IpAllowlistBlock.find(new RuntimeException((String) null))).isEmpty();
    assertThat(IpAllowlistBlock.find(null)).isEmpty();
  }
}
