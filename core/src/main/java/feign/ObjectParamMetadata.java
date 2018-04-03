/**
 * Copyright 2012-2018 The Feign Authors
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

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

class ObjectParamMetadata {

  private final static ConcurrentMap<Class<?>, ObjectParamMetadata> classToMetadata =
      new ConcurrentHashMap<Class<?>, ObjectParamMetadata>();

  private final List<Field> objectFields;

  private ObjectParamMetadata (List<Field> objectFields) {
    this.objectFields = Collections.unmodifiableList(objectFields);
  }

  Map<String, Object> toQueryMap(Object object) throws IllegalAccessException {
    Map<String, Object> fieldNameToValue = new LinkedHashMap<String, Object>();
    for (Field field : objectFields) {
      Object value = field.get(object);
      fieldNameToValue.put(field.getName(), value);
    }
    return fieldNameToValue;
  }

  static ObjectParamMetadata getMetadata(Class<?> objectType) {
    ObjectParamMetadata metadata = classToMetadata.get(objectType);
    if (metadata == null) {
      metadata = parseObjectType(objectType);
      classToMetadata.putIfAbsent(objectType, metadata);
    }
    return metadata;
  }

  private static ObjectParamMetadata parseObjectType(Class<?> type) {
    List<Field> fields = new ArrayList<Field>();
    for (Field field : type.getDeclaredFields()) {
      if (!field.isAccessible()) {
        field.setAccessible(true);
      }
      fields.add(field);
    }
    return new ObjectParamMetadata(fields);
  }
}