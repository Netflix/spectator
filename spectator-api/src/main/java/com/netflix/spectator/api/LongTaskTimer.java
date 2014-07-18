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
package com.netflix.spectator.api;

/**
 * Timer intended to track a small number of long running tasks. Example would be something like
 * a batch hadoop job. Though "long running" is a bit subjective the assumption is that anything
 * over a minute is long running.
 */
public interface LongTaskTimer extends Meter {
  /**
   * Start keeping time for a task and return a task id that can be used to look up how long
   * it has been running.
   */
  long start();

  /**
   * Indicates that a given task has completed.
   *
   * @param task
   *     Id for the task to stop. This should be the value returned from {@link #start()}.
   * @return
   *     Duration for the task in nanoseconds. A -1 value will be returned for an unknown task.
   */
  long stop(long task);

  /**
   * Returns the current duration for a given active task.
   *
   * @param task
   *     Id for the task to stop. This should be the value returned from {@link #start()}.
   * @return
   *     Duration for the task in nanoseconds. A -1 value will be returned for an unknown task.
   */
  long duration(long task);

  /** Returns the cumulative duration of all current tasks in nanoseconds. */
  long duration();

  /** Returns the current number of tasks being executed. */
  int activeTasks();
}
