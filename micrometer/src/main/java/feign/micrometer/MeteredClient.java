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

import feign.*;
import feign.Request.Options;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import java.io.IOException;
import static feign.micrometer.MetricTagResolver.EMPTY_TAGS_ARRAY;

/**
 * Warp feign {@link Client} with metrics.
 */
public class MeteredClient implements Client {

  private final Client client;
  private final MeterRegistry meterRegistry;
  private final MetricName metricName;
  private final MetricTagResolver metricTagResolver;

  public MeteredClient(Client client, MeterRegistry meterRegistry) {
    this(client, meterRegistry, new FeignMetricName(Client.class), new FeignMetricTagResolver());
  }

  public MeteredClient(Client client,
      MeterRegistry meterRegistry,
      MetricName metricName,
      MetricTagResolver metricTagResolver) {
    this.client = client;
    this.meterRegistry = meterRegistry;
    this.metricName = metricName;
    this.metricTagResolver = metricTagResolver;
  }

  @Override
  public Response execute(Request request, Options options) throws IOException {
    final Timer.Sample sample = Timer.start(meterRegistry);
    Timer timer = null;
    try {
      final Response response = client.execute(request, options);
      countResponseCode(request, response, options, response.status(), null);
      timer = createTimer(request, response, options, null);
      sample.stop(timer);
      return response;
    } catch (FeignException e) {
      timer = createTimer(request, null, options, e);
      countResponseCode(request, null, options, e.status(), e);
      throw e;
    } catch (IOException | RuntimeException e) {
      timer = createTimer(request, null, options, e);
      throw e;
    } catch (Exception e) {
      timer = createTimer(request, null, options, e);
      throw new IOException(e);
    } finally {
      if (timer == null) {
        timer = createTimer(request, null, options, null);
      }
      sample.stop(timer);
    }
  }

  protected void countResponseCode(Request request,
                                   Response response,
                                   Options options,
                                   int responseStatus,
                                   Exception e) {
    final Tag[] extraTags = extraTags(request, response, options, e);
    final RequestTemplate template = request.requestTemplate();
    final Tags allTags = metricTagResolver
        .tag(template.methodMetadata(), template.feignTarget(), e,
            new Tag[] {Tag.of("http_status", String.valueOf(responseStatus)),
                Tag.of("status_group", responseStatus / 100 + "xx"),
                Tag.of("uri", template.path())})
        .and(extraTags);
    meterRegistry.counter(
        metricName.name("http_response_code"),
        allTags)
        .increment();
  }

  protected Timer createTimer(Request request,
                              Response response,
                              Options options,
                              Exception e) {
    final RequestTemplate template = request.requestTemplate();
    final Tags allTags = metricTagResolver
        .tag(template.methodMetadata(), template.feignTarget(), e, Tag.of("uri", template.path()))
        .and(extraTags(request, response, options, e));
    return meterRegistry.timer(metricName.name(e), allTags);
  }

  protected Tag[] extraTags(Request request,
                            Response response,
                            Options options,
                            Exception e) {
    return EMPTY_TAGS_ARRAY;
  }
}
