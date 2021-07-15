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

import static feign.Util.UTF_8;
import java.io.*;
import java.nio.charset.Charset;
import java.util.function.Supplier;
import feign.Response.Body;

/**
 * {@link Body} implementation that keeps track of how many bytes are read.
 */
public final class MeteredBody implements Body {

  private final Body delegate;
  private Supplier<Long> count;

  public MeteredBody(Body body) {
    this.delegate = body;
    count = () -> 0L;
  }

  @Override
  public void close() throws IOException {
    delegate.close();
  }

  @Override
  public Integer length() {
    return delegate.length();
  }

  @Override
  public boolean isRepeatable() {
    return delegate.isRepeatable();
  }

  @Override
  public InputStream asInputStream() throws IOException {
    // TODO, ideally, would like not to bring guava just for this
    final CountingInputStream input = new CountingInputStream(delegate.asInputStream());
    count = input::getCount;
    return input;
  }

  @Override
  public Reader asReader() throws IOException {
    return new InputStreamReader(asInputStream(), UTF_8);
  }

  public long count() {
    return count.get();
  }

  @Override
  public Reader asReader(Charset charset) throws IOException {
    return new InputStreamReader(asInputStream(), charset);
  }

}
