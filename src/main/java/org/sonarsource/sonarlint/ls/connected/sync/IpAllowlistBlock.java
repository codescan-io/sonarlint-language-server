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

import java.util.Optional;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/**
 * Finds CodeScan's "blocked by its IP allowlist" message in a failure, so it can be shown instead of a generic error.
 */
public final class IpAllowlistBlock {
  static final String MARKER = "is blocked by its IP allowlist";
  static final String ADVICE = "Ask an administrator of the organization to add your network to the IP allowlist.";
  private static final Pattern JSON_MSG = Pattern.compile("\"msg\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

  private IpAllowlistBlock() {
  }

  public static Optional<String> find(@Nullable Throwable failure) {
    var seen = 0;
    for (var t = failure; t != null && seen < 20; t = t.getCause() == t ? null : t.getCause(), seen++) {
      var message = t.getMessage();
      if (message != null && message.contains(MARKER)) {
        return Optional.of(fromJson(message).orElse(message));
      }
    }
    return Optional.empty();
  }

  private static Optional<String> fromJson(String message) {
    var matcher = JSON_MSG.matcher(message);
    while (matcher.find()) {
      var msg = unescapeJson(matcher.group(1));
      if (msg.contains(MARKER)) {
        return Optional.of(msg);
      }
    }
    return Optional.empty();
  }

  /** Decodes JSON string escapes; the server writes the quote around the organization key as '. */
  private static String unescapeJson(String value) {
    var out = new StringBuilder(value.length());
    for (var i = 0; i < value.length(); i++) {
      var c = value.charAt(i);
      if (c != '\\' || i + 1 >= value.length()) {
        out.append(c);
        continue;
      }
      var next = value.charAt(++i);
      if (next == 'u' && i + 4 < value.length()) {
        try {
          out.append((char) Integer.parseInt(value.substring(i + 1, i + 5), 16));
          i += 4;
        } catch (NumberFormatException e) {
          out.append('\\').append(next);
        }
      } else if (next == 'n') {
        out.append('\n');
      } else if (next == 't') {
        out.append('\t');
      } else {
        out.append(next);
      }
    }
    return out.toString();
  }
}
