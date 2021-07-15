/**
 * Copyright 2012-2021 The Feign Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package feign.micrometer;


public final class FeignMetricName implements MetricName {

  private final Class<?> meteredComponent;

  public FeignMetricName(Class<?> meteredComponent) {
    this.meteredComponent = meteredComponent;
  }

  @Override
  public String name(String suffix) {
    return name()
        // any separator, so naming convention can change it
        + "." + suffix;
  }

  @Override
  public String name() {
    return meteredComponent.getName();
  }

  @Override
  public String name(Throwable e) {
    if (e == null) {
      return name();
    }
    return name("exception");
  }
}
