/*
 * Copyright 2014-2019 Netflix, Inc.
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
package com.netflix.spectator.ipc;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeoutException;

public class IpcStatusTest {

  @Test
  public void forHttpStatusNegative() {
    Assertions.assertEquals(IpcStatus.unexpected_error, IpcStatus.forHttpStatus(-1));
  }

  @Test
  public void forHttpStatus1xx() {
    Assertions.assertEquals(IpcStatus.success, IpcStatus.forHttpStatus(100));
  }

  @Test
  public void forHttpStatus2xx() {
    Assertions.assertEquals(IpcStatus.success, IpcStatus.forHttpStatus(200));
  }

  @Test
  public void forHttpStatus3xx() {
    Assertions.assertEquals(IpcStatus.success, IpcStatus.forHttpStatus(304));
  }

  @Test
  public void forHttpStatus404() {
    Assertions.assertEquals(IpcStatus.success, IpcStatus.forHttpStatus(404));
  }

  @Test
  public void forHttpStatus403() {
    Assertions.assertEquals(IpcStatus.access_denied, IpcStatus.forHttpStatus(403));
  }

  @Test
  public void forHttpStatus429() {
    Assertions.assertEquals(IpcStatus.throttled, IpcStatus.forHttpStatus(429));
  }

  @Test
  public void forHttpStatus4xx() {
    Assertions.assertEquals(IpcStatus.bad_request, IpcStatus.forHttpStatus(487));
  }

  @Test
  public void forHttpStatus503() {
    Assertions.assertEquals(IpcStatus.unavailable, IpcStatus.forHttpStatus(503));
  }

  @Test
  public void forHttpStatus5xx() {
    Assertions.assertEquals(IpcStatus.unexpected_error, IpcStatus.forHttpStatus(587));
  }

  @Test
  public void forHttpStatusTooBig() {
    Assertions.assertEquals(IpcStatus.unexpected_error, IpcStatus.forHttpStatus(123456));
  }

  @Test
  public void forExceptionIO() {
    Throwable t = new IOException();
    Assertions.assertEquals(IpcStatus.connection_error, IpcStatus.forException(t));
  }

  @Test
  public void forExceptionSocket() {
    Throwable t = new SocketException();
    Assertions.assertEquals(IpcStatus.connection_error, IpcStatus.forException(t));
  }

  @Test
  public void forExceptionUnknownHost() {
    Throwable t = new UnknownHostException();
    Assertions.assertEquals(IpcStatus.connection_error, IpcStatus.forException(t));
  }

  @Test
  public void forExceptionConnect() {
    Throwable t = new ConnectException();
    Assertions.assertEquals(IpcStatus.connection_error, IpcStatus.forException(t));
  }

  @Test
  public void forExceptionTimeout() {
    Throwable t = new TimeoutException();
    Assertions.assertEquals(IpcStatus.timeout, IpcStatus.forException(t));
  }

  @Test
  public void forExceptionSocketTimeout() {
    Throwable t = new SocketTimeoutException();
    Assertions.assertEquals(IpcStatus.timeout, IpcStatus.forException(t));
  }

  @Test
  public void forExceptionIllegalArgument() {
    Throwable t = new IllegalArgumentException();
    Assertions.assertEquals(IpcStatus.bad_request, IpcStatus.forException(t));
  }

  @Test
  public void forExceptionIllegalState() {
    Throwable t = new IllegalStateException();
    Assertions.assertEquals(IpcStatus.bad_request, IpcStatus.forException(t));
  }

  @Test
  public void forExceptionRuntime() {
    Throwable t = new RuntimeException();
    Assertions.assertEquals(IpcStatus.unexpected_error, IpcStatus.forException(t));
  }

  @Test
  public void forExceptionSSL() {
    Throwable t = new SSLException("test");
    Assertions.assertEquals(IpcStatus.access_denied, IpcStatus.forException(t));
  }
}
