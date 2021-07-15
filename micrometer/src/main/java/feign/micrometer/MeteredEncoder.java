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

import feign.RequestTemplate;
import feign.codec.EncodeException;
import feign.codec.Encoder;
import io.micrometer.core.instrument.*;
import java.lang.reflect.Type;
import static feign.micrometer.MetricTagResolver.EMPTY_TAGS_ARRAY;

/**
 * Warp feign {@link Encoder} with metrics.
 */
public class MeteredEncoder implements Encoder {

  private final Encoder encoder;
  private final MeterRegistry meterRegistry;
  private final MetricName metricName;
  private final MetricTagResolver metricTagResolver;

  public MeteredEncoder(Encoder encoder, MeterRegistry meterRegistry) {
    this(encoder, meterRegistry, new FeignMetricName(Encoder.class), new FeignMetricTagResolver());
  }

  public MeteredEncoder(Encoder encoder,
      MeterRegistry meterRegistry,
      MetricName metricName,
      MetricTagResolver metricTagResolver) {
    this.encoder = encoder;
    this.meterRegistry = meterRegistry;
    this.metricName = metricName;
    this.metricTagResolver = metricTagResolver;
  }

  @Override
  public void encode(Object object, Type bodyType, RequestTemplate template)
      throws EncodeException {
    createTimer(object, bodyType, template)
        .record(() -> encoder.encode(object, bodyType, template));

    if (template.body() != null) {
      createSummary(object, bodyType, template).record(template.body().length);
    }
  }

  protected Timer createTimer(Object object, Type bodyType, RequestTemplate template) {
    final Tags allTags = metricTagResolver.tag(template.methodMetadata(), template.feignTarget(),
        extraTags(object, bodyType, template));
    return meterRegistry.timer(metricName.name(), allTags);
  }

  protected DistributionSummary createSummary(Object object,
                                              Type bodyType,
                                              RequestTemplate template) {
    final Tags allTags = metricTagResolver.tag(template.methodMetadata(), template.feignTarget(),
        extraTags(object, bodyType, template));
    return meterRegistry.summary(metricName.name("response_size"), allTags);
  }

  protected Tag[] extraTags(Object object, Type bodyType, RequestTemplate template) {
    return EMPTY_TAGS_ARRAY;
  }
}
