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

import com.netflix.client.config.DefaultClientConfigImpl;

/**
 * Customize some of the default settings used for rest clients.
 */
public class RibbonClientConfigImpl extends DefaultClientConfigImpl {

  @Override
  public String getNameSpace() {
    return "niws.client";
  }

  @Override
  public String getDefaultClientClassname() {
    return "com.netflix.spectator.ribbon.MeteredRestClient";
  }

  @Override
  public String getDefaultSeverListClass() {
    return "com.netflix.niws.loadbalancer.DiscoveryEnabledNIWSServerList";
  }
}
