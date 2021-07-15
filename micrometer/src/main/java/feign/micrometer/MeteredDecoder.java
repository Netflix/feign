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


import feign.FeignException;
import feign.RequestTemplate;
import feign.Response;
import feign.codec.Decoder;
import io.micrometer.core.instrument.*;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Optional;
import static feign.micrometer.MetricTagResolver.EMPTY_TAGS_ARRAY;

/**
 * Warp feign {@link Decoder} with metrics.
 */
public class MeteredDecoder implements Decoder {

  private final Decoder decoder;
  private final MeterRegistry meterRegistry;
  private final MetricName metricName;
  private final MetricTagResolver metricTagResolver;

  public MeteredDecoder(Decoder decoder, MeterRegistry meterRegistry) {
    this(decoder, meterRegistry, new FeignMetricName(Decoder.class), new FeignMetricTagResolver());
  }

  public MeteredDecoder(Decoder decoder, MeterRegistry meterRegistry, MetricName metricName,
      MetricTagResolver metricTagResolver) {
    this.decoder = decoder;
    this.meterRegistry = meterRegistry;
    this.metricName = metricName;
    this.metricTagResolver = metricTagResolver;
  }

  @Override
  public Object decode(Response response, Type type)
      throws IOException, FeignException {
    final Optional<MeteredBody> body = Optional.ofNullable(response.body())
        .map(MeteredBody::new);

    Response meteredResponse = body.map(b -> response.toBuilder().body(b).build())
        .orElse(response);

    Object decoded;

    final Timer.Sample sample = Timer.start(meterRegistry);
    Timer timer = null;
    try {
      decoded = decoder.decode(meteredResponse, type);
      timer = createTimer(response, type, null);
      sample.stop(timer);
    } catch (IOException | RuntimeException e) {
      timer = createTimer(response, type, e);
      createExceptionCounter(response, type, e).count();
      throw e;
    } catch (Exception e) {
      timer = createTimer(response, type, e);
      createExceptionCounter(response, type, e).count();
      throw new IOException(e);
    } finally {
      if (timer == null) {
        timer = createTimer(response, type, null);
      }
      sample.stop(timer);
    }

    body.ifPresent(b -> createSummary(response, type).record(b.count()));

    return decoded;
  }

  protected Timer createTimer(Response response, Type type, Exception e) {
    final Tag[] extraTags = extraTags(response, type, e);
    final RequestTemplate template = response.request().requestTemplate();
    final Tags allTags =
        metricTagResolver.tag(template.methodMetadata(), template.feignTarget(), e, extraTags);
    return meterRegistry.timer(metricName.name(e), allTags);
  }

  protected Counter createExceptionCounter(Response response, Type type, Exception e) {
    final Tag[] extraTags = extraTags(response, type, e);
    final RequestTemplate template = response.request().requestTemplate();
    final Tags allTags = metricTagResolver.tag(template.methodMetadata(), template.feignTarget(),
        Tag.of("uri", template.path()),
        Tag.of("exception_name", e.getClass().getSimpleName()))
        .and(extraTags);
    return meterRegistry.counter(metricName.name("error_count"), allTags);
  }

  protected DistributionSummary createSummary(Response response, Type type) {
    final Tag[] tags = extraTags(response, type, null);
    final RequestTemplate template = response.request().requestTemplate();
    final Tags allTags =
        metricTagResolver.tag(template.methodMetadata(), template.feignTarget(), tags);
    return meterRegistry.summary(metricName.name("response_size"), allTags);
  }

  protected Tag[] extraTags(Response response, Type type, Exception e) {
    return EMPTY_TAGS_ARRAY;
  }
}
