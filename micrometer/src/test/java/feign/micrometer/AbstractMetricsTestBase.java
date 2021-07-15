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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;
import feign.Capability;
import feign.Feign;
import feign.FeignException;
import feign.RequestLine;
import feign.mock.HttpMethod;
import feign.mock.MockClient;
import feign.mock.MockTarget;

public abstract class AbstractMetricsTestBase<MR, METRIC_ID, METRIC> {

  public interface SimpleSource {

    @RequestLine("GET /get")
    String get(String body);

  }

  protected MR metricsRegistry;

  @Before
  public final void initializeMetricRegistry() {
    this.metricsRegistry = createMetricsRegistry();
  }

  protected abstract MR createMetricsRegistry();

  @Test
  public final void addMetricsCapability() {
    final SimpleSource source = Feign.builder()
        .client(new MockClient()
            .ok(HttpMethod.GET, "/get", "1234567890abcde"))
        .addCapability(createMetricCapability())
        .target(new MockTarget<>(SimpleSource.class));

    source.get("0x3456789");

    Map<METRIC_ID, METRIC> metrics = getFeignMetrics();
    assertThat(metrics, aMapWithSize(7));
    metrics.keySet().forEach(metricId -> assertThat(
        "Expect all metric names to include client name:" + metricId,
        doesMetricIdIncludeClient(metricId)));
    metrics.keySet().forEach(metricId -> assertThat(
        "Expect all metric names to include method name:" + metricId,
        doesMetricIncludeVerb(metricId, "get")));
    metrics.keySet().forEach(metricId -> assertThat(
        "Expect all metric names to include host name:" + metricId,
        doesMetricIncludeHost(metricId)));
  }

  protected abstract boolean doesMetricIncludeHost(METRIC_ID metricId);

  protected abstract boolean doesMetricIncludeVerb(METRIC_ID metricId, String verb);

  protected abstract boolean doesMetricIdIncludeClient(METRIC_ID metricId);

  protected abstract Capability createMetricCapability();

  protected abstract Map<METRIC_ID, METRIC> getFeignMetrics();


  @Test
  public void clientPropagatesUncheckedException() {
    final AtomicReference<FeignException.NotFound> notFound = new AtomicReference<>();

    final SimpleSource source = Feign.builder()
        .client((request, options) -> {
          notFound.set(new FeignException.NotFound("test", request, null, null));
          throw notFound.get();
        })
        .addCapability(createMetricCapability())
        .target(new MockTarget<>(MicrometerCapabilityTest.SimpleSource.class));

    try {
      source.get("0x3456789");
      fail("Should throw NotFound exception");
    } catch (FeignException.NotFound e) {
      assertSame(notFound.get(), e);
    }

    assertThat(getMetric("http_response_code", "http_status", "404", "status_group", "4xx"),
        notNullValue());
  }


  protected abstract METRIC getMetric(String suffix, String... tags);

  @Test
  public void decoderPropagatesUncheckedException() {
    final AtomicReference<FeignException.NotFound> notFound = new AtomicReference<>();

    final SimpleSource source = Feign.builder()
        .client(new MockClient()
            .ok(HttpMethod.GET, "/get", "1234567890abcde"))
        .decoder((response, type) -> {
          notFound.set(new FeignException.NotFound("test", response.request(), null, null));
          throw notFound.get();
        })
        .addCapability(createMetricCapability())
        .target(new MockTarget<>(MicrometerCapabilityTest.SimpleSource.class));

    try {
      source.get("0x3456789");
      fail("Should throw NotFound exception");
    } catch (FeignException.NotFound e) {
      assertSame(notFound.get(), e);
    }
  }

}
