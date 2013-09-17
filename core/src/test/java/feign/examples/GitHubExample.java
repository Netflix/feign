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
package feign.examples;

import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import feign.Feign;
import feign.Logger;
import feign.RequestLine;
import feign.Response;
import feign.codec.Decoder;

import javax.inject.Named;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.util.List;

import static feign.Util.ensureClosed;

/**
 * adapted from {@code com.example.retrofit.GitHubClient}
 */
public class GitHubExample {

  interface GitHub {
    @RequestLine("GET /repos/{owner}/{repo}/contributors")
    List<Contributor> contributors(@Named("owner") String owner, @Named("repo") String repo);
  }

  static class Contributor {
    String login;
    int contributions;
  }

  public static void main(String... args) {
    GitHub github = Feign.builder()
                         .logger(new Logger.ErrorLogger())
                         .logLevel(Logger.Level.BASIC)
                         .decoder(new GsonDecoder())
                         .target(GitHub.class, "https://api.github.com");

    System.out.println("Let's fetch and print a list of the contributors to this library.");
    List<Contributor> contributors = github.contributors("netflix", "feign");
    for (Contributor contributor : contributors) {
      System.out.println(contributor.login + " (" + contributor.contributions + ")");
    }
  }

  /**
   * Here's how it looks to write a decoder.  Note: you can instead use {@code feign-gson}!
   */
  static class GsonDecoder implements Decoder {
    private final Gson gson = new Gson();

    @Override public Object decode(Response response, Type type) throws IOException {
      if (void.class == type || response.body() == null) {
        return null;
      }
      Reader reader = response.body().asReader();
      try {
        return gson.fromJson(reader, type);
      } catch (JsonIOException e) {
        if (e.getCause() != null && e.getCause() instanceof IOException) {
          throw IOException.class.cast(e.getCause());
        }
        throw e;
      } finally {
        ensureClosed(reader);
      }
    }
  }
}
