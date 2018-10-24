/*
 * Copyright 2014-2018 Netflix, Inc.
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
package com.netflix.spectator.ipcservlet;

import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Meter;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Utils;
import com.netflix.spectator.ipc.IpcTagKey;
import org.junit.Assert;

import java.util.stream.Stream;

final class TestUtils {

  private TestUtils() {
  }

  static Stream<Id> clientCallMetrics(Registry registry) {
    return registry
        .stream()
        .map(Meter::id)
        .filter(id -> "ipc.client.call".equals(id.name()));
  }

  static Stream<Id> serverCallMetrics(Registry registry) {
    return registry
        .stream()
        .map(Meter::id)
        .filter(id -> "ipc.server.call".equals(id.name()));
  }

  static void checkEndpoint(Id id, String expected) {
    String endpoint = Utils.getTagValue(id, IpcTagKey.endpoint.key());
    Assert.assertEquals(expected, endpoint);
  }

  static void checkClientEndpoint(Registry registry, String expected) {
    Assert.assertTrue(clientCallMetrics(registry).count() > 0);
    clientCallMetrics(registry).forEach(id -> checkEndpoint(id, expected));
  }

  static void checkServerEndpoint(Registry registry, String expected) {
    Assert.assertTrue(serverCallMetrics(registry).count() > 0);
    serverCallMetrics(registry).forEach(id -> checkEndpoint(id, expected));
  }

  static void checkEndpoint(Registry registry, String expected) {
    checkClientEndpoint(registry, expected);
    checkServerEndpoint(registry, expected);
  }

  static void checkStatus(Id id, String expected) {
    String endpoint = Utils.getTagValue(id, IpcTagKey.statusDetail.key());
    Assert.assertEquals("HTTP_" + expected, endpoint);
  }

  static void checkClientStatus(Registry registry, String expected) {
    Assert.assertTrue(clientCallMetrics(registry).count() > 0);
    clientCallMetrics(registry).forEach(id -> checkStatus(id, expected));
  }

  static void checkServerStatus(Registry registry, String expected) {
    Assert.assertTrue(serverCallMetrics(registry).count() > 0);
    serverCallMetrics(registry).forEach(id -> checkStatus(id, expected));
  }

  static void checkStatus(Registry registry, String expected) {
    checkClientStatus(registry, expected);
    checkServerStatus(registry, expected);
  }

  static void checkMethod(Id id, String expected) {
    String endpoint = Utils.getTagValue(id, IpcTagKey.httpMethod.key());
    Assert.assertEquals(expected, endpoint);
  }

  static void checkClientMethod(Registry registry, String expected) {
    Assert.assertTrue(clientCallMetrics(registry).count() > 0);
    clientCallMetrics(registry).forEach(id -> checkMethod(id, expected));
  }

  static void checkServerMethod(Registry registry, String expected) {
    Assert.assertTrue(serverCallMetrics(registry).count() > 0);
    serverCallMetrics(registry).forEach(id -> checkMethod(id, expected));
  }

  static void checkMethod(Registry registry, String expected) {
    checkClientMethod(registry, expected);
    checkServerMethod(registry, expected);
  }

  static void checkErrorReason(Id id, String expected) {
    String endpoint = Utils.getTagValue(id, IpcTagKey.statusDetail.key());
    Assert.assertEquals(expected, endpoint);
  }

  static void checkClientErrorReason(Registry registry, String expected) {
    Assert.assertTrue(clientCallMetrics(registry).count() > 0);
    clientCallMetrics(registry).forEach(id -> checkErrorReason(id, expected));
  }

  static void checkServerErrorReason(Registry registry, String expected) {
    Assert.assertTrue(serverCallMetrics(registry).count() > 0);
    serverCallMetrics(registry).forEach(id -> checkErrorReason(id, expected));
  }

  static void checkErrorReason(Registry registry, String expected) {
    checkClientErrorReason(registry, expected);
    checkServerErrorReason(registry, expected);
  }

  static void checkResult(Id id, String expected) {
    String endpoint = Utils.getTagValue(id, IpcTagKey.result.key());
    Assert.assertEquals(expected, endpoint);
  }

  static void checkClientResult(Registry registry, String expected) {
    Assert.assertTrue(clientCallMetrics(registry).count() > 0);
    clientCallMetrics(registry).forEach(id -> checkResult(id, expected));
  }

  static void checkServerResult(Registry registry, String expected) {
    Assert.assertTrue(serverCallMetrics(registry).count() > 0);
    serverCallMetrics(registry).forEach(id -> checkResult(id, expected));
  }

  static void checkResult(Registry registry, String expected) {
    checkClientResult(registry, expected);
    checkServerResult(registry, expected);
  }
}
