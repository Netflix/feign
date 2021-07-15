/**
 * Copyright 2012-2019 The Feign Authors
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
package feign.codec;

import static feign.codec.ErrorDecoder.RetryAfterDecoder.RFC822_FORMAT;
import static org.junit.Assert.assertEquals;

import feign.codec.ErrorDecoder.XRateLimitResetDecoder;
import java.text.ParseException;
import org.junit.Test;

public class XRateLimitResetDecoderTest {

  private XRateLimitResetDecoder decoder = new XRateLimitResetDecoder() {
    protected long currentTimeMillis() {
      try {
        return RFC822_FORMAT.parse("Sat, 1 Jan 2000 00:00:00 GMT").getTime();
      } catch (ParseException e) {
        throw new RuntimeException(e);
      }
    }
  };

  @Test
  public void epochSecondsParses() throws ParseException {
    assertEquals(RFC822_FORMAT.parse("Sat, 1 Jan 2000 01:00:00 GMT"),
        decoder.apply(Long.toString(RFC822_FORMAT.parse("Sat, 1 Jan 2000 01:00:00 GMT").getTime() / 1000)));
  }

  @Test
  public void relativeSecondsParses() throws ParseException {
    assertEquals(RFC822_FORMAT.parse("Sun, 1 Jan 2000 01:00:00 GMT"), decoder.apply("3600"));
  }
}
