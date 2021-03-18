/**
 * Copyright 2012-2020 The Feign Authors
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
package feign.querymap;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import feign.Param;
import feign.QueryMapEncoder;
import feign.codec.EncodeException;

/**
 * the query map will be generated using member variable names as query parameter names.
 *
 * eg: "/uri?name={name}&number={number}"
 *
 * order of included query parameters not guaranteed, and as usual, if any value is null, it will be
 * left out
 */
public class FieldQueryMapEncoder implements QueryMapEncoder {

  private final Map<Class<?>, ObjectParamMetadata> classToMetadata =
      new ConcurrentHashMap<>();

  @Override
  public Map<String, Object> encode(Object object) throws EncodeException {
    try {
      AtomicReference<Object> atomicReference = new AtomicReference<>();
      atomicReference.set(object);

      ObjectParamMetadata metadata = getMetadata(atomicReference.get().getClass());

      Map<String, Object> fieldNameToValue = new ConcurrentHashMap<>();
      for (Field field : metadata.objectFields) {
        Object value = field.get(atomicReference.get());
        if (value != null && value != atomicReference.get()) {
          Param alias = field.getAnnotation(Param.class);
          String name = alias != null ? alias.value() : field.getName();
          fieldNameToValue.put(name, value);
        }
      }
      return fieldNameToValue;
    } catch (IllegalAccessException e) {
      throw new EncodeException("Failure encoding object into query map", e);
    }
  }

  private ObjectParamMetadata getMetadata(Class<?> objectType) {

    ObjectParamMetadata metadata;

    if (classToMetadata.containsKey(objectType)) {
      metadata = classToMetadata.get(objectType);
    } else {
      metadata = ObjectParamMetadata.parseObjectType(objectType);
      classToMetadata.put(objectType, metadata);
    }

    return metadata;
  }

  private static class ObjectParamMetadata {

    private final List<Field> objectFields;

    private ObjectParamMetadata(List<Field> objectFields) {
      this.objectFields = Collections.unmodifiableList(objectFields);
    }

    private static ObjectParamMetadata parseObjectType(Class<?> type) {
      List<Field> allFields = new ArrayList();

      for (Class currentClass = type; currentClass != null; currentClass =
          currentClass.getSuperclass()) {
        Collections.addAll(allFields, currentClass.getDeclaredFields());
      }

      return new ObjectParamMetadata(allFields.stream()
          .filter(field -> !field.isSynthetic())
          .peek(field -> field.setAccessible(true))
          .collect(Collectors.toList()));
    }
  }
}
