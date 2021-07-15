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

import feign.MethodMetadata;
import feign.Target;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import java.lang.reflect.Method;

public interface MetricTagResolver {

  Tag[] EMPTY_TAGS_ARRAY = new Tag[] {};

  Tags tag(MethodMetadata methodMetadata, Target<?> target, Tag... tags);

  Tags tag(MethodMetadata methodMetadata, Target<?> target, Throwable e, Tag... tags);

  Tags tag(Class<?> targetType, Method method, String url, Tag... extraTags);

  Tags tag(Class<?> targetType, Method method, String url, Throwable e, Tag... extraTags);
}
