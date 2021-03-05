/*
 * Copyright 2014-2021 Netflix, Inc.
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
package com.netflix.spectator.api.patterns;

import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;

/**
 * Base type for builders that are required to construct an {@link Id} object. This class
 * assists with the creation of a base id and then returns another builder type that can
 * be used for further refinement. By following this pattern the builder will force the
 * user to satisfy the minimal constraints of the id before a final option to construct
 * or complete the operation is provided.
 *
 * <p>Sample usage:</p>
 *
 * <pre>
 *   return new IdBuilder<Builder>(registry) {
 *     {@literal @}Override protected Builder createTypeBuilder(Id id) {
 *       return new Builder(registry, id);
 *     }
 *   };
 * </pre>
 *
 * <p>The type returned by {@link #createTypeBuilder(Id)} can extend {@link TagsBuilder}
 * to easily support additional tags being added after the base id requirements are
 * satisfied.</p>
 */
public abstract class IdBuilder<T> {

  /** Registry used for created ids. */
  protected final Registry registry;

  /** Create a new instance. */
  protected IdBuilder(Registry registry) {
    this.registry = registry;
  }

  /** Sub-classes should override this method to create the more specific builder type. */
  protected abstract T createTypeBuilder(Id id);

  /**
   * Create a new identifier with the provide name.
   *
   * @param name
   *     Name used in the identifier.
   * @return
   *     Builder object to allow for method chaining.
   */
  public T withName(String name) {
    return createTypeBuilder(registry.createId(name));
  }

  /**
   * Set the base identifier for the type being constructed.
   *
   * @param id
   *     Identifier for the type being constructed.
   * @return
   *     Builder object to allow for method chaining.
   */
  public T withId(Id id) {
    if (id == null) {
      registry.propagate(new NullPointerException("parameter 'id' cannot be null"));
      return createTypeBuilder(registry.createId(null));
    } else {
      return createTypeBuilder(id);
    }
  }
}
