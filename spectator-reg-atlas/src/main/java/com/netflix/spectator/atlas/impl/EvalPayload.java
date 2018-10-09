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
package com.netflix.spectator.atlas.impl;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Wraps a list of measurements with a set of common tags. The common tags are
 * typically used for things like the application and instance id.
 *
 * <b>Classes in this package are only intended for use internally within spectator. They may
 * change at any time and without notice.</b>
 */
public final class EvalPayload {

  private final long timestamp;
  private final List<Metric> metrics;
  private final List<Message> messages;

  /** Create a new instance. */
  public EvalPayload(long timestamp, List<Metric> metrics, List<Message> messages) {
    this.timestamp = timestamp;
    this.metrics = metrics;
    this.messages = messages;
  }

  /** Create a new instance. */
  public EvalPayload(long timestamp, List<Metric> metrics) {
    this(timestamp, metrics, Collections.emptyList());
  }

  /** Return the timestamp for metrics in this payload. */
  public long getTimestamp() {
    return timestamp;
  }

  /** Return the metric values for the data in this payload. */
  public List<Metric> getMetrics() {
    return metrics;
  }

  /** Return any diagnostic messages that should be sent back to the user. */
  public List<Message> getMessages() {
    return messages;
  }

  @Override public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    EvalPayload payload = (EvalPayload) o;
    return timestamp == payload.timestamp
        && metrics.equals(payload.metrics)
        && messages.equals(payload.messages);
  }

  @Override public int hashCode() {
    int result = (int) (timestamp ^ (timestamp >>> 32));
    result = 31 * result + metrics.hashCode();
    result = 31 * result + messages.hashCode();
    return result;
  }

  @Override public String toString() {
    return "EvalPayload(timestamp=" + timestamp
        + ", metrics=" + metrics
        + ", messages=" + messages + ")";
  }

  /** Metric value. */
  public static final class Metric {
    private final String id;
    private final Map<String, String> tags;
    private final double value;

    /** Create a new instance. */
    public Metric(String id, Map<String, String> tags, double value) {
      this.id = id;
      this.tags = tags;
      this.value = value;
    }

    /** Id for the expression that this data corresponds with. */
    public String getId() {
      return id;
    }

    /** Tags for identifying the metric. */
    public Map<String, String> getTags() {
      return tags;
    }

    /** Value for the metric. */
    public double getValue() {
      return value;
    }

    @Override public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Metric metric = (Metric) o;
      return Double.compare(metric.value, value) == 0
          && id.equals(metric.id)
          && tags.equals(metric.tags);
    }

    @Override public int hashCode() {
      int result;
      long temp;
      result = id.hashCode();
      result = 31 * result + tags.hashCode();
      temp = Double.doubleToLongBits(value);
      result = 31 * result + (int) (temp ^ (temp >>> 32));
      return result;
    }

    @Override public String toString() {
      return "Metric(id=" + id + ", tags=" + tags + ", value=" + value + ")";
    }
  }

  /** Message. */
  @SuppressWarnings("PMD.AvoidFieldNameMatchingTypeName")
  public static final class Message {
    private final String id;
    private final DiagnosticMessage message;

    /** Create a new instance. */
    public Message(String id, DiagnosticMessage message) {
      this.id = id;
      this.message = message;
    }

    /** Id for the expression that resulted in this message. */
    public String getId() {
      return id;
    }

    /** Message to send back to the user. */
    public DiagnosticMessage getMessage() {
      return message;
    }

    @Override public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Message msg = (Message) o;
      return id.equals(msg.id) && message.equals(msg.message);
    }

    @Override public int hashCode() {
      int result;
      result = id.hashCode();
      result = 31 * result + message.hashCode();
      return result;
    }

    @Override public String toString() {
      return "Message(id=" + id + ", message=" + message + ")";
    }
  }

  /** Diagnostic message. */
  public static final class DiagnosticMessage {
    private final MessageType type;
    private final String message;

    /** Create a new instance. */
    public DiagnosticMessage(MessageType type, String message) {
      this.type = type;
      this.message = message;
    }

    /**
     * Type of the message. Indicates whether it is purely informational or if there was
     * a problem the user needs to handle.
     */
    public MessageType getType() {
      return type;
    }

    /** Description of the problem. */
    public String getMessage() {
      return message;
    }

    @Override public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      DiagnosticMessage msg = (DiagnosticMessage) o;
      return type == msg.type && message.equals(msg.message);
    }

    @Override public int hashCode() {
      int result;
      result = type.hashCode();
      result = 31 * result + message.hashCode();
      return result;
    }

    @Override public String toString() {
      return "DiagnosticMessage(type=" + type + ", message=" + message + ")";
    }
  }

  /** Message type. */
  public enum MessageType {

    /** Informational notices that are primarily to aide in debugging. */
    info,

    /**
     * Notifies the user of something that went wrong or that they should change, but that
     * is not causing an immediate problem.
     */
    warn,

    /** Expression cannot be handled will be rejected. */
    error;
  }
}
