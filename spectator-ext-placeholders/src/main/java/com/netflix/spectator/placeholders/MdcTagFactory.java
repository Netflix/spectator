/**
 * Copyright 2016 Netflix, Inc.
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
package com.netflix.spectator.placeholders;

import com.netflix.spectator.api.BasicTag;
import com.netflix.spectator.api.Tag;
import com.netflix.spectator.impl.Preconditions;
import org.slf4j.MDC;

/**
 * A TagFactory implementation that extracts information from the Sl4fj MDC data.
 * If the MDC for the current thread has no value associated with the specified
 * name at the time that the createTag method is invoked, then that method will
 * return null, which will result in the tag being omitted.
 */
public class MdcTagFactory implements TagFactory {

  private final String name;

  /**
   * Construct a new instance that will return a Tag with the MDC value associated
   * with the specified name at the time that the createTag method is invoked.
   *
   * @param name
   *        the non-null name of the MDC value to use for the tag
   */
  public MdcTagFactory(String name) {
    this.name = Preconditions.checkNotNull(name, "name");
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public Tag createTag() {
    String value = MDC.get(name);

    return value != null ? new BasicTag(name, value) : null;
  }
}
