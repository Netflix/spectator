/**
 * Copyright 2014 Netflix, Inc.
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
package com.netflix.spectator.ribbon;

import com.netflix.client.ClientException;
import com.netflix.client.config.IClientConfig;
import com.netflix.client.http.HttpRequest;
import com.netflix.client.http.HttpResponse;
import com.netflix.niws.client.http.RestClient;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Spectator;
import com.netflix.spectator.api.Timer;

import java.util.concurrent.TimeUnit;

/**
 * Subclass that provides instrumentation of requests, latency, and failures.
 */
public class MeteredRestClient extends RestClient {

  private Timer latency;

  private Id requests;
  private Id exceptions;

  private Id niwsRequests;
  private Id niwsExceptions;

  @Override
  public void initWithNiwsConfig(IClientConfig config) {
    super.initWithNiwsConfig(config);

    final String client = "client";
    final String cname = getClientName();
    latency = Spectator.registry().timer("ribbon.http.latency", client, cname);

    requests = Spectator.registry().createId("ribbon.http.requests", client, cname);
    exceptions = Spectator.registry().createId("ribbon.http.exceptions", client, cname);

    niwsRequests = Spectator.registry().createId("ribbon.http.niwsRequests", client, cname);
    niwsExceptions = Spectator.registry().createId("ribbon.http.niwsExceptions", client, cname);
  }

  @Override
  public HttpResponse execute(HttpRequest req) throws Exception {
    final long start = System.nanoTime();
    try {
      final HttpResponse res = super.execute(req);
      final String status = String.format("%d", res.getStatus());
      Spectator.registry().counter(requests.withTag("status", status)).increment();
      return res;
    } catch (ClientException e) {
      final String m = e.getErrorType().name();
      Spectator.registry().counter(exceptions.withTag("error", m)).increment();
      throw e;
    } catch (Exception e) {
      final String c = e.getClass().getSimpleName();
      Spectator.registry().counter(exceptions.withTag("error", c)).increment();
      throw e;
    } finally {
      latency.record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
    }
  }

  @Override
  public HttpResponse executeWithLoadBalancer(HttpRequest req) throws ClientException {
    final long start = System.nanoTime();
    try {
      final HttpResponse res = super.executeWithLoadBalancer(req);
      final String status = String.format("%d", res.getStatus());
      Spectator.registry().counter(niwsRequests.withTag("status", status)).increment();
      return res;
    } catch (ClientException e) {
      final String m = e.getErrorType().name();
      Spectator.registry().counter(niwsExceptions.withTag("error", m)).increment();
      throw e;
    } catch (Exception e) {
      final String c = e.getClass().getSimpleName();
      Spectator.registry().counter(niwsExceptions.withTag("error", c)).increment();
      throw e;
    } finally {
      latency.record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
    }
  }
}
