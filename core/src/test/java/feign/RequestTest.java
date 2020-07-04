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
package feign;

import feign.Request.HttpMethod;
import java.util.Collections;
import org.junit.Test;
import static feign.assertj.FeignAssertions.assertThat;

public class RequestTest {

  @Test
  public void testNullBodyShouldBeReplacedByEmptyConstant() {
    Request request = Request.create(HttpMethod.GET,
        "https://github.com/OpenFeign/feign",
        Collections.emptyMap(),
        (Request.Body) null,
        (RequestTemplate) null);
    assertThat(request.body()).isEqualTo(Request.Body.EMPTY.asBytes());
  }

}
