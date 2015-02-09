/**
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spectator.http;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Pair that includes a list of servers and the creation time for the entry.
 */
final class ServerEntry {
  private final List<Server> servers;
  private final long ctime;

  private final AtomicInteger nextPos = new AtomicInteger(0);

  /** Create a new instance. */
  ServerEntry(List<Server> servers, long ctime) {
    this.servers = servers;
    this.ctime = ctime;
  }

  /** Return the list of servers. */
  List<Server> servers() {
    return servers;
  }

  /** Return the creation time for this entry. */
  long ctime() {
    return ctime;
  }

  /**
   * Return a list of {@code n} servers based on the servers from the entry. The starting point in
   * the full server list will increase on each access. If {@code n} is larger than the number of
   * servers in the list then servers will be used multiple times.
   */
  List<Server> next(int n) {
    List<Server> out = new ArrayList<>(n);
    int size = servers.size();
    for (int i = 0, j = nextPos.getAndIncrement(); i < n; ++i, ++j) {
      out.add(servers.get(j % size));
    }
    return out;
  }

}
