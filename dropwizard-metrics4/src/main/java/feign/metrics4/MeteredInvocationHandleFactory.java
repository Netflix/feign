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
package feign.metrics4;


import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import feign.*;

/**
 * Warp feign {@link InvocationHandler} with metrics.
 */
public class MeteredInvocationHandleFactory implements InvocationHandlerFactory {

  private static final Logger LOG = LoggerFactory.getLogger(MeteredInvocationHandleFactory.class);

  /**
   * Methods that are declared by super class object and, if invoked, we don't wanna record metrics
   * for
   */
  private static final List<String> JAVA_OBJECT_METHODS =
      Arrays.asList("equals", "toString", "hashCode");

  private final InvocationHandlerFactory invocationHandler;

  private final MetricRegistry metricRegistry;

  private final FeignMetricName metricName;

  private final MetricSuppliers metricSuppliers;

  public MeteredInvocationHandleFactory(InvocationHandlerFactory invocationHandler,
      MetricRegistry metricRegistry, MetricSuppliers metricSuppliers) {
    this.invocationHandler = invocationHandler;
    this.metricRegistry = metricRegistry;
    this.metricSuppliers = metricSuppliers;
    this.metricName = new FeignMetricName(Feign.class);
  }

  @Override
  public InvocationHandler create(Target target, Map<Method, MethodHandler> dispatch) {
    final Class clientClass = target.type();

    final InvocationHandler invocationHandle = invocationHandler.create(target, dispatch);
    return (proxy, method, args) -> {

      if (JAVA_OBJECT_METHODS.contains(method.getName())
          || Util.isDefault(method)) {
        LOG.trace("Skipping metrics for method={}", method);
        return invocationHandle.invoke(proxy, method, args);
      }

      try (final Timer.Context classTimer =
          metricRegistry.timer(metricName.metricName(clientClass, method, target.url()),
              metricSuppliers.timers()).time()) {

        return invocationHandle.invoke(proxy, method, args);
      } catch (final FeignException e) {
        metricRegistry.meter(
            MetricRegistry.name(metricName.metricName(clientClass, method, target.url()),
                "http_error", e.status() / 100 + "xx", String.valueOf(e.status())),
            metricSuppliers.meters()).mark();

        throw e;
      } catch (final Throwable e) {
        metricRegistry.meter(
            MetricRegistry.name(metricName.metricName(clientClass, method, target.url()),
                "exception", e.getClass().getSimpleName()),
            metricSuppliers.meters())
            .mark();

        throw e;
      }
    };
  }

  @Override
  public InvocationHandlerFactory withFeignFactory(SharedParameters.FeignFactory feignFactory) {
    return new MeteredInvocationHandleFactory(invocationHandler.withFeignFactory(feignFactory),
        metricRegistry, metricSuppliers);
  }
}
