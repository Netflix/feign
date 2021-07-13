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
package feign.error;

import feign.Response;
import feign.codec.Decoder;
import feign.codec.ErrorDecoder;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import static feign.Feign.configKey;

public class AnnotationErrorDecoder implements ErrorDecoder {

  private final Map<String, MethodErrorHandler> errorHandlerMap;
  private final ErrorDecoder defaultDecoder;


  AnnotationErrorDecoder(Map<String, MethodErrorHandler> errorHandlerMap,
      ErrorDecoder defaultDecoder) {
    this.errorHandlerMap = errorHandlerMap;
    this.defaultDecoder = defaultDecoder;
  }

  @Override
  public Exception decode(String methodKey, Response response) {
    if (errorHandlerMap.containsKey(methodKey)) {
      return errorHandlerMap.get(methodKey).decode(response);
    }
    return defaultDecoder.decode(methodKey, response);
  }


  public static AnnotationErrorDecoder.Builder builderFor(Class<?> apiType) {
    return new Builder(apiType);
  }

  public static class Builder {
    private final Class<?> apiType;
    private ErrorDecoder defaultDecoder = new ErrorDecoder.Default();
    private Decoder responseBodyDecoder = new Decoder.Default();


    public Builder(Class<?> apiType) {
      this.apiType = apiType;
    }

    public Builder withDefaultDecoder(ErrorDecoder defaultDecoder) {
      this.defaultDecoder = defaultDecoder;
      return this;
    }

    public Builder withResponseBodyDecoder(Decoder responseBodyDecoder) {
      this.responseBodyDecoder = responseBodyDecoder;
      return this;
    }

    public AnnotationErrorDecoder build() {
      Map<String, MethodErrorHandler> errorHandlerMap = generateErrorHandlerMapFromApi(apiType);
      return new AnnotationErrorDecoder(errorHandlerMap, defaultDecoder);
    }

    Map<String, MethodErrorHandler> generateErrorHandlerMapFromApi(Class<?> apiType) {

      ExceptionGenerator classLevelDefault = new ExceptionGenerator.Builder()
          .withResponseBodyDecoder(responseBodyDecoder)
          .withExceptionType(ErrorHandling.NO_DEFAULT.class)
          .build();
      Map<Integer, ExceptionGenerator> classLevelStatusCodeDefinitions =
          new HashMap<Integer, ExceptionGenerator>();

      Optional<ErrorHandling> classLevelErrorHandling =
          readErrorHandlingIncludingInherited(apiType);
      if (classLevelErrorHandling.isPresent()) {
        ErrorHandlingDefinition classErrorHandlingDefinition =
            readAnnotation(classLevelErrorHandling.get(), responseBodyDecoder);
        classLevelDefault = classErrorHandlingDefinition.defaultThrow;
        classLevelStatusCodeDefinitions = classErrorHandlingDefinition.statusCodesMap;
      }

      Map<String, MethodErrorHandler> methodErrorHandlerMap =
          new HashMap<String, MethodErrorHandler>();
      for (Method method : apiType.getMethods()) {
        ErrorHandling methodLevelAnnotation = getErrorHandlingAnnotation(method);
        if (methodLevelAnnotation != null) {
          ErrorHandlingDefinition methodErrorHandling =
              readAnnotation(methodLevelAnnotation, responseBodyDecoder);
          ExceptionGenerator methodDefault = methodErrorHandling.defaultThrow;
          if (methodDefault.getExceptionType().equals(ErrorHandling.NO_DEFAULT.class)) {
            methodDefault = classLevelDefault;
          }

          MethodErrorHandler methodErrorHandler =
              new MethodErrorHandler(methodErrorHandling.statusCodesMap,
                  classLevelStatusCodeDefinitions, methodDefault);

          methodErrorHandlerMap.put(configKey(apiType, method), methodErrorHandler);
        }
      }

      return methodErrorHandlerMap;
    }

    Optional<ErrorHandling> readErrorHandlingIncludingInherited(Class<?> apiType) {
      ErrorHandling apiTypeAnnotation = getErrorHandlingAnnotation(apiType);
      if (apiTypeAnnotation != null) {
        return Optional.of(apiTypeAnnotation);
      }
      for (Class<?> parentInterface : apiType.getInterfaces()) {
        Optional<ErrorHandling> errorHandling =
            readErrorHandlingIncludingInherited(parentInterface);
        if (errorHandling.isPresent()) {
          return errorHandling;
        }
      }
      // Finally, if there's a superclass that isn't Object check if the superclass has anything
      if (!apiType.isInterface() && !apiType.getSuperclass().equals(Object.class)) {
        return readErrorHandlingIncludingInherited(apiType.getSuperclass());
      }
      return Optional.empty();
    }

    private static ErrorHandling getErrorHandlingAnnotation(AnnotatedElement element) {
      ErrorHandling annotation = element.getAnnotation(ErrorHandling.class);
      if (annotation == null) {
        for (Annotation metaAnnotation : element.getAnnotations()) {
          annotation = metaAnnotation.annotationType().getAnnotation(ErrorHandling.class);
          if (annotation != null) {
            break;
          }
        }
      }
      return annotation;
    }

    static ErrorHandlingDefinition readAnnotation(ErrorHandling errorHandling,
                                                  Decoder responseBodyDecoder) {
      ExceptionGenerator defaultException = new ExceptionGenerator.Builder()
          .withResponseBodyDecoder(responseBodyDecoder)
          .withExceptionType(errorHandling.defaultException())
          .build();
      Map<Integer, ExceptionGenerator> statusCodesDefinition =
          new HashMap<Integer, ExceptionGenerator>();

      for (ErrorCodes statusCodeDefinition : errorHandling.codeSpecific()) {
        for (int statusCode : statusCodeDefinition.codes()) {
          if (statusCodesDefinition.containsKey(statusCode)) {
            throw new IllegalStateException(
                "Status Code [" + statusCode + "] " +
                    "has already been declared to throw ["
                    + statusCodesDefinition.get(statusCode).getExceptionType().getName() + "] " +
                    "and [" + statusCodeDefinition.generate() + "] - dupe definition");
          }
          statusCodesDefinition.put(statusCode,
              new ExceptionGenerator.Builder()
                  .withResponseBodyDecoder(responseBodyDecoder)
                  .withExceptionType(statusCodeDefinition.generate())
                  .build());
        }
      }

      return new ErrorHandlingDefinition(defaultException, statusCodesDefinition);
    }

    private static class ErrorHandlingDefinition {
      private final ExceptionGenerator defaultThrow;
      private final Map<Integer, ExceptionGenerator> statusCodesMap;


      private ErrorHandlingDefinition(ExceptionGenerator defaultThrow,
          Map<Integer, ExceptionGenerator> statusCodesMap) {
        this.defaultThrow = defaultThrow;
        this.statusCodesMap = statusCodesMap;
      }
    }
  }
}
