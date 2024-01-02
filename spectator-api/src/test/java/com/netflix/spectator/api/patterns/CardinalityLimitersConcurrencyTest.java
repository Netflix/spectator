/*
 * Copyright 2014-2024 Netflix, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spectator.api.patterns;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.netflix.spectator.api.patterns.CardinalityLimiters.OTHERS;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CardinalityLimitersConcurrencyTest {

  private final ExecutorService pool = Executors.newFixedThreadPool(100);

  @Test
  void firstLimiterWhenContentionSizeIsRespected() throws InterruptedException, ExecutionException {
    Function<String, String> limiter = CardinalityLimiters.first(100);

    // when: 100 threads try to register the same 1..100 numbers with contention
    Callable<List<String>> task = () -> IntStream
        .rangeClosed(1, 100)
        .mapToObj(j -> limiter.apply(String.valueOf(j))).collect(Collectors.toList());
    List<Future<List<String>>> handles = IntStream.rangeClosed(1, 100)
        .mapToObj(i -> pool.submit(task))
        .collect(Collectors.toList());
    for (Future<List<String>> handle : handles) {
      handle.get();
    }

    // then: expect each number to have been registered despite the contention
    //  since the bound has not been reached. (i.e OTHERS must not be present)
    List<String> appliedNumbers = new ArrayList<>();
    for (int i = 1; i <= 100; i++) {
      String applied = limiter.apply(String.valueOf(i));
      appliedNumbers.add(applied);
    }

    assertTrue(appliedNumbers.stream().noneMatch(n -> n.equals(OTHERS)),
        () -> "at least one number was not registered in the limiter, "
            + String.join(",", appliedNumbers));
  }
}
