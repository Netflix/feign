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

import dagger.ObjectGraph;
import dagger.Provides;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.xml.sax.helpers.DefaultHandler;

import javax.inject.Inject;
import javax.inject.Provider;
import java.io.IOException;
import java.io.StringReader;
import java.text.ParseException;
import java.util.Set;

import static dagger.Provides.Type.SET;
import static org.testng.Assert.assertEquals;

// unbound wildcards are not currently injectable in dagger.
@SuppressWarnings("rawtypes")
public class SAXDecoderTest {

  @dagger.Module(injects = SAXDecoderTest.class)
  static class Module {
    @Provides(type = SET) Decoder saxDecoder(Provider<NetworkStatusHandler> networkStatus, //
                                             Provider<NetworkStatusStringHandler> networkStatusAsString) {
      return SAXDecoder.builder() //
          .addContentHandler(networkStatus) //
          .addContentHandler(networkStatusAsString) //
          .build();
    }
  }

  @Inject Set<Decoder> decoders;

  @BeforeClass void inject() {
    ObjectGraph.create(new Module()).inject(this);
  }

  @Test public void parsesConfiguredTypes() throws ParseException, IOException {
    Decoder decoder = decoders.iterator().next();
    assertEquals(decoder.decode(new StringReader(statusFailed), NetworkStatus.class), NetworkStatus.FAILED);
    assertEquals(decoder.decode(new StringReader(statusFailed), String.class), "Failed");
  }

  @Test(expectedExceptions = IllegalStateException.class, expectedExceptionsMessageRegExp =
      "type int not in configured handlers \\[class .*NetworkStatus, class java.lang.String\\]")
  public void niceErrorOnUnconfiguredType() throws ParseException, IOException {
    Decoder decoder = decoders.iterator().next();
    decoder.decode(new StringReader(statusFailed), int.class);
  }

  static String statusFailed = ""//
      + "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">\n"//
      + "  <soap:Body>\n"//
      + "    <ns1:getNeustarNetworkStatusResponse xmlns:ns1=\"http://webservice.api.ultra.neustar.com/v01/\">\n"//
      + "      <NeustarNetworkStatus xmlns:ns2=\"http://schema.ultraservice.neustar.com/v01/\">Failed</NeustarNetworkStatus>\n"//
      + "    </ns1:getNeustarNetworkStatusResponse>\n"//
      + "  </soap:Body>\n"//
      + "</soap:Envelope>";

  static enum NetworkStatus {
    GOOD, FAILED;
  }

  static class NetworkStatusStringHandler extends DefaultHandler implements
      SAXDecoder.ContentHandlerWithResult<String> {
    @Inject NetworkStatusStringHandler() {
    }

    private StringBuilder currentText = new StringBuilder();

    private String status;

    @Override
    public String result() {
      return status;
    }

    @Override
    public void endElement(String uri, String name, String qName) {
      if (qName.equals("NeustarNetworkStatus")) {
        this.status = currentText.toString().trim();
      }
      currentText = new StringBuilder();
    }

    @Override
    public void characters(char ch[], int start, int length) {
      currentText.append(ch, start, length);
    }
  }

  static class NetworkStatusHandler extends DefaultHandler implements
      SAXDecoder.ContentHandlerWithResult<NetworkStatus> {
    @Inject NetworkStatusHandler() {
    }

    private StringBuilder currentText = new StringBuilder();

    private NetworkStatus status;

    @Override
    public NetworkStatus result() {
      return status;
    }

    @Override
    public void endElement(String uri, String name, String qName) {
      if (qName.equals("NeustarNetworkStatus")) {
        this.status = NetworkStatus.valueOf(currentText.toString().trim().toUpperCase());
      }
      currentText = new StringBuilder();
    }

    @Override
    public void characters(char ch[], int start, int length) {
      currentText.append(ch, start, length);
    }
  }
}
