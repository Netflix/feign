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


import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import feign.Feign;
import feign.FeignException;
import feign.InvocationHandlerFactory;
import feign.SharedParameters;
import feign.Target;
import feign.Util;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;

/**
 * Warp feign {@link InvocationHandler} with metrics.
 */
public class MeteredInvocationHandleFactory implements InvocationHandlerFactory {

  /**
   * Methods that are declared by super class object and, if invoked, we don't wanna record metrics
   * for
   */
  private static final List<String> JAVA_OBJECT_METHODS =
      Arrays.asList("equals", "toString", "hashCode");

  private final InvocationHandlerFactory invocationHandler;

  private final MeterRegistry meterRegistry;

  private final MetricName metricName;

  public MeteredInvocationHandleFactory(InvocationHandlerFactory invocationHandler,
      MeterRegistry meterRegistry) {
    this(invocationHandler, meterRegistry, new FeignMetricName(Feign.class));
  }

  public MeteredInvocationHandleFactory(InvocationHandlerFactory invocationHandler,
      MeterRegistry meterRegistry, MetricName metricName) {
    this.invocationHandler = invocationHandler;
    this.meterRegistry = meterRegistry;
    this.metricName = metricName;
  }

  @Override
  public InvocationHandler create(Target target, Map<Method, MethodHandler> dispatch) {
    final Class clientClass = target.type();

    final InvocationHandler invocationHandle = invocationHandler.create(target, dispatch);
    return (proxy, method, args) -> {

      if (JAVA_OBJECT_METHODS.contains(method.getName())
          || Util.isDefault(method)) {
        return invocationHandle.invoke(proxy, method, args);
      }

      try {
        return meterRegistry.timer(
            metricName.name(),
            metricName.tag(clientClass, method, target.url()))
            .recordCallable(() -> {
              try {
                return invocationHandle.invoke(proxy, method, args);
              } catch (Exception e) {
                throw e;
              } catch (Throwable e) {
                throw new Exception(e);
              }
            });
      } catch (final FeignException e) {
        meterRegistry.counter(
            metricName.name("http_error"),
            metricName.tag(clientClass, method, target.url(),
                Tag.of("http_status", String.valueOf(e.status())),
                Tag.of("error_group", e.status() / 100 + "xx")))
            .increment();

        throw e;
      } catch (final Throwable e) {
        meterRegistry.counter(
            metricName.name("exception"),
            metricName.tag(clientClass, method, target.url(),
                Tag.of("exception_name", e.getClass().getSimpleName())))
            .increment();

        throw e;
      }
    };
  }

  @Override
  public InvocationHandlerFactory withFeignFactory(SharedParameters.FeignFactory feignFactory) {
    return new MeteredInvocationHandleFactory(invocationHandler.withFeignFactory(feignFactory),
        meterRegistry, metricName);
  }
}
