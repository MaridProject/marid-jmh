package org.marid.jmh;

/*-
 * #%L
 * maven-jmh
 * %%
 * Copyright (C) 2020 MARID software development group
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.locks.LockSupport;

@Fork(value = 1)
@Measurement(iterations = 1)
@Warmup(iterations = 1)
public class TestBenchmark {

  @Benchmark
  public void benchmark() {
    LockSupport.parkNanos(1L);
  }
}
