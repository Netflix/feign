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
package feign;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * Controls reflective method dispatch.
 */
public interface InvocationHandlerFactory {

  InvocationHandler create(Target target, Map<Method, MethodHandler> dispatch);

  default InvocationHandlerFactory withFeignFactory(SharedParameters.FeignFactory feignFactory) {
    return this;
  }

  /**
   * Like {@link InvocationHandler#invoke(Object, java.lang.reflect.Method, Object[])}, except for a
   * single method.
   */
  interface MethodHandler {

    Object invoke(Object[] argv) throws Throwable;
  }

  final class Default implements InvocationHandlerFactory {

    private final SharedParameters.FeignFactory feignFactory;

    public Default() {
      this(null);
    }

    private Default(SharedParameters.FeignFactory feignFactory) {
      this.feignFactory = feignFactory;
    }

    @Override
    public InvocationHandlerFactory withFeignFactory(SharedParameters.FeignFactory feignFactory) {
      return new Default(feignFactory);
    }

    @Override
    public InvocationHandler create(Target target, Map<Method, MethodHandler> dispatch) {
      return new ReflectiveFeign.FeignInvocationHandler(target, dispatch, feignFactory);
    }
  }
}
