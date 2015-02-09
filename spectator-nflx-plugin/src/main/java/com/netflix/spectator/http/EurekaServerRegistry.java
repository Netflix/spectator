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

import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.DiscoveryClient;
import com.netflix.spectator.impl.Preconditions;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Registry that gets the server list from eureka. Servers that have a status of UP and are
 * registered with the requested vip will get used.
 */
public class EurekaServerRegistry implements ServerRegistry {

  // If the list is over 15s old, refresh from discovery
  private static final long EXPIRATION_TIME = TimeUnit.SECONDS.toMillis(15);

  private final ConcurrentHashMap<String, ServerEntry> serversByVip = new ConcurrentHashMap<>();

  private final DiscoveryClient client;

  /** Create a new instance. */
  @Inject
  public EurekaServerRegistry(DiscoveryClient client) {
    this.client = client;
  }

  @Override
  public boolean isStillAvailable(Server server) {
    List<InstanceInfo> instances = client.getInstancesById(server.id());
    for (InstanceInfo info : instances) {
      if (info.getStatus() == InstanceInfo.InstanceStatus.UP) {
        return true;
      }
    }
    return false;
  }

  @Override
  public List<Server> getServers(String vip, ClientConfig clientCfg) {
    Preconditions.checkNotNull(vip, "vipAddress");
    long now = System.currentTimeMillis();
    ServerEntry entry = serversByVip.get(vip);
    if (entry == null || now - entry.ctime() > EXPIRATION_TIME) {
      ServerEntry tmp = new ServerEntry(getServersImpl(vip, clientCfg), now);
      entry = serversByVip.putIfAbsent(vip, tmp);
      return next(vip, (entry == null) ? tmp : entry, clientCfg);
    } else {
      return next(vip, entry, clientCfg);
    }
  }

  private List<Server> next(String vip, ServerEntry entry, ClientConfig clientCfg) {
    int numAttempts = clientCfg.numRetries() + 1;
    List<Server> servers = entry.next(numAttempts);
    if (servers.isEmpty()) {
      throw new IllegalStateException("no UP servers for vip: " + vip);
    }
    return servers;
  }

  private List<Server> getServersImpl(String vip, ClientConfig clientCfg) {
    List<InstanceInfo> instances = client.getInstancesByVipAddress(vip, clientCfg.isSecure());
    List<Server> filtered = new ArrayList<>(instances.size());
    for (InstanceInfo info : instances) {
      if (info.getStatus() == InstanceInfo.InstanceStatus.UP) {
        filtered.add(toServer(clientCfg, info));
      }
    }
    Collections.shuffle(filtered);
    return filtered;
  }

  /** Convert a eureka InstanceInfo object to a server. */
  static Server toServer(ClientConfig clientCfg, InstanceInfo instance) {
    String host = clientCfg.useIpAddress() ? instance.getIPAddr() : instance.getHostName();
    int dfltPort = clientCfg.isSecure() ? instance.getSecurePort() : instance.getPort();
    int port = clientCfg.port(dfltPort);
    return new Server(instance.getId(), host, port, clientCfg.isSecure());
  }
}
