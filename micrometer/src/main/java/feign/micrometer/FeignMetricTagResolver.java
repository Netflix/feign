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
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class FeignMetricTagResolver implements MetricTagResolver {

  @Override
  public Tags tag(MethodMetadata methodMetadata, Target<?> target, Tag... tags) {
    return tag(methodMetadata, target, null, tags);
  }

  @Override
  public Tags tag(MethodMetadata methodMetadata, Target<?> target, Throwable e, Tag... tags) {
    return tag(methodMetadata.targetType(), methodMetadata.method(), target.url(), tags);
  }

  @Override
  public Tags tag(Class<?> targetType, Method method, String url, Tag... extraTags) {
    return tag(targetType, method, url, null, extraTags);
  }

  @Override
  public Tags tag(Class<?> targetType, Method method, String url, Throwable e, Tag... extraTags) {
    List<Tag> tags = new ArrayList<>(defaultTags());
    tags.add(Tag.of("client", targetType.getName()));
    tags.add(Tag.of("method", method.getName()));
    tags.add(Tag.of("host", extractHost(url)));
    if (e != null) {
      tags.add(Tag.of("exception_name", e.getClass().getSimpleName()));
    }
    tags.addAll(Arrays.asList(extraTags));
    return Tags.of(tags);
  }

  protected List<Tag> defaultTags() {
    return Collections.emptyList();
  }

  private String extractHost(final String targetUrl) {
    try {
      String host = new URI(targetUrl).getHost();
      if (host != null)
        return host;
    } catch (final URISyntaxException e) {
    }

    // can't get the host, in that case, just read first 20 chars from url
    return targetUrl.length() <= 20
        ? targetUrl
        : targetUrl.substring(0, 20);
  }
}
