/*
 * Copyright 2013 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package feign.codec;

import feign.Response;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.CharBuffer;

import static feign.Util.ensureClosed;

/**
 * Adapted from {@code com.google.common.io.CharStreams.toString()}.
 */
public class StringDecoder implements Decoder {
  private static final int BUF_SIZE = 0x800; // 2K chars (4K bytes)

  @Override
  public Object decode(Response response, Type type) throws IOException {
    Response.Body body = response.body();
    if (body == null) {
      return null;
    }
    Reader from = body.asReader();
    try {
      StringBuilder to = new StringBuilder();
      CharBuffer buf = CharBuffer.allocate(BUF_SIZE);
      while (from.read(buf) != -1) {
        buf.flip();
        to.append(buf);
        buf.clear();
      }
      return to.toString();
    } finally {
      ensureClosed(from);
    }
  }
}
