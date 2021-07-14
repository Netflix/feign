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

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import feign.Contract.BaseContract;

/**
 * {@link Contract} base implementation that works by declaring witch annotations should be
 * processed and how each annotation modifies {@link MethodMetadata}
 */
public abstract class DeclarativeContract extends BaseContract {

  private final List<GuardedAnnotationProcessor> classAnnotationProcessors = new ArrayList<>();
  private final List<GuardedAnnotationProcessor> methodAnnotationProcessors = new ArrayList<>();
  private final Map<Class<Annotation>, DeclarativeContract.ParameterAnnotationProcessor<Annotation>> parameterAnnotationProcessors =
      new HashMap<>();

  @Override
  public final List<MethodMetadata> parseAndValidateMetadata(Class<?> targetType) {
    // any implementations must register processors
    return super.parseAndValidateMetadata(targetType);
  }

  /**
   * Called by parseAndValidateMetadata twice, first on the declaring class, then on the target type
   * (unless they are the same).
   *
   * @param data metadata collected so far relating to the current java method.
   * @param targetType the class to process
   */
  @Override
  protected final void processAnnotationOnClass(MethodMetadata data, Class<?> targetType) {
    final List<GuardedAnnotationProcessor> processors = Arrays.stream(targetType.getAnnotations())
        .flatMap(annotation -> classAnnotationProcessors.stream()
            .filter(processor -> processor.test(annotation)))
        .collect(Collectors.toList());

    if (!processors.isEmpty()) {
      Arrays.stream(targetType.getAnnotations())
          .forEach(annotation -> processors.stream()
              .filter(processor -> processor.test(annotation))
              .forEach(processor -> processor.process(annotation, data)));
    } else {
      if (targetType.getAnnotations().length == 0) {
        data.addWarning(String.format(
            "Class %s has no annotations, it may affect contract %s",
            targetType.getSimpleName(),
            getClass().getSimpleName()));
      } else {
        data.addWarning(String.format(
            "Class %s has annotations %s that are not used by contract %s",
            targetType.getSimpleName(),
            Arrays.stream(targetType.getAnnotations())
                .map(annotation -> annotation.annotationType()
                    .getSimpleName())
                .collect(Collectors.toList()),
            getClass().getSimpleName()));
      }
    }
  }

  /**
   * @param data metadata collected so far relating to the current java method.
   * @param annotation annotations present on the current method annotation.
   * @param method method currently being processed.
   */
  @Override
  protected final void processAnnotationOnMethod(MethodMetadata data,
                                                 Annotation annotation,
                                                 Method method) {
    List<GuardedAnnotationProcessor> processors = methodAnnotationProcessors.stream()
        .filter(processor -> processor.test(annotation))
        .collect(Collectors.toList());

    if (!processors.isEmpty()) {
      processors.forEach(processor -> processor.process(annotation, data));
    } else {
      data.addWarning(String.format(
          "Method %s has an annotation %s that is not used by contract %s",
          method.getName(),
          annotation.annotationType()
              .getSimpleName(),
          getClass().getSimpleName()));
    }
  }


  /**
   * @param data metadata collected so far relating to the current java method.
   * @param annotations annotations present on the current parameter annotation.
   * @param paramIndex if you find a name in {@code annotations}, call
   *        {@link #nameParam(MethodMetadata, String, int)} with this as the last parameter.
   * @return true if you called {@link #nameParam(MethodMetadata, String, int)} after finding an
   *         http-relevant annotation.
   */
  @Override
  protected final boolean processAnnotationsOnParameter(MethodMetadata data,
                                                        Annotation[] annotations,
                                                        int paramIndex) {
    List<Annotation> matchingAnnotations = Arrays.stream(annotations)
        .filter(
            annotation -> parameterAnnotationProcessors.containsKey(annotation.annotationType()))
        .collect(Collectors.toList());

    if (!matchingAnnotations.isEmpty()) {
      matchingAnnotations.forEach(annotation -> parameterAnnotationProcessors
          .getOrDefault(annotation.annotationType(), ParameterAnnotationProcessor.DO_NOTHING)
          .process(annotation, data, paramIndex));

    } else {
      final Parameter parameter = data.method().getParameters()[paramIndex];
      String parameterName = parameter.isNamePresent()
          ? parameter.getName()
          : parameter.getType().getSimpleName();
      if (annotations.length == 0) {
        data.addWarning(String.format(
            "Parameter %s has no annotations, it may affect contract %s",
            parameterName,
            getClass().getSimpleName()));
      } else {
        data.addWarning(String.format(
            "Parameter %s has annotations %s that are not used by contract %s",
            parameterName,
            Arrays.stream(annotations)
                .map(annotation -> annotation.annotationType()
                    .getSimpleName())
                .collect(Collectors.toList()),
            getClass().getSimpleName()));
      }
    }
    return false;
  }

  /**
   * Called while class annotations are being processed
   *
   * @param annotationType to be processed
   * @param processor function that defines the annotations modifies {@link MethodMetadata}
   */
  protected <E extends Annotation> void registerClassAnnotation(Class<E> annotationType,
                                                                DeclarativeContract.AnnotationProcessor<E> processor) {
    registerClassAnnotation(
        annotation -> annotation.annotationType().equals(annotationType),
        processor);
  }

  /**
   * Called while class annotations are being processed
   *
   * @param predicate to check if the annotation should be processed or not
   * @param processor function that defines the annotations modifies {@link MethodMetadata}
   */
  protected <E extends Annotation> void registerClassAnnotation(Predicate<E> predicate,
                                                                DeclarativeContract.AnnotationProcessor<E> processor) {
    this.classAnnotationProcessors.add(new GuardedAnnotationProcessor(predicate, processor));
  }

  /**
   * Called while method annotations are being processed
   *
   * @param annotationType to be processed
   * @param processor function that defines the annotations modifies {@link MethodMetadata}
   */
  protected <E extends Annotation> void registerMethodAnnotation(Class<E> annotationType,
                                                                 DeclarativeContract.AnnotationProcessor<E> processor) {
    registerMethodAnnotation(
        annotation -> annotation.annotationType().equals(annotationType),
        processor);
  }

  /**
   * Called while method annotations are being processed
   *
   * @param predicate to check if the annotation should be processed or not
   * @param processor function that defines the annotations modifies {@link MethodMetadata}
   */
  protected <E extends Annotation> void registerMethodAnnotation(Predicate<E> predicate,
                                                                 DeclarativeContract.AnnotationProcessor<E> processor) {
    this.methodAnnotationProcessors.add(new GuardedAnnotationProcessor(predicate, processor));
  }

  /**
   * Called while method parameter annotations are being processed
   *
   * @param annotation to be processed
   * @param processor function that defines the annotations modifies {@link MethodMetadata}
   */
  protected <E extends Annotation> void registerParameterAnnotation(Class<E> annotation,
                                                                    DeclarativeContract.ParameterAnnotationProcessor<E> processor) {
    this.parameterAnnotationProcessors.put((Class) annotation,
        (DeclarativeContract.ParameterAnnotationProcessor) processor);
  }

  @FunctionalInterface
  public interface AnnotationProcessor<E extends Annotation> {

    /**
     * @param annotation present on the current element.
     * @param metadata collected so far relating to the current java method.
     */
    void process(E annotation, MethodMetadata metadata);
  }

  @FunctionalInterface
  public interface ParameterAnnotationProcessor<E extends Annotation> {

    DeclarativeContract.ParameterAnnotationProcessor<Annotation> DO_NOTHING = (ann, data, i) -> {
    };

    /**
     * @param annotation present on the current parameter annotation.
     * @param metadata metadata collected so far relating to the current java method.
     * @param paramIndex if you find a name in {@code annotations}, call
     *        {@link #nameParam(MethodMetadata, String, int)} with this as the last parameter.
     * @return true if you called {@link #nameParam(MethodMetadata, String, int)} after finding an
     *         http-relevant annotation.
     */
    void process(E annotation, MethodMetadata metadata, int paramIndex);
  }

  private class GuardedAnnotationProcessor
      implements Predicate<Annotation>, DeclarativeContract.AnnotationProcessor<Annotation> {

    private final Predicate<Annotation> predicate;
    private final DeclarativeContract.AnnotationProcessor<Annotation> processor;

    @SuppressWarnings({"rawtypes", "unchecked"})
    private GuardedAnnotationProcessor(Predicate predicate,
        DeclarativeContract.AnnotationProcessor processor) {
      this.predicate = predicate;
      this.processor = processor;
    }

    @Override
    public void process(Annotation annotation, MethodMetadata metadata) {
      processor.process(annotation, metadata);
    }

    @Override
    public boolean test(Annotation t) {
      return predicate.test(t);
    }

  }

}
