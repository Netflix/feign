package feign.codec;

import static feign.Util.UTF_8;
import static org.junit.Assert.*;
import org.junit.Test;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.*;
import feign.*;
import feign.Request.HttpMethod;
import feign.codec.FifoDecoderTest.FakeDecoder;

public class FifoDecoderTest {

  public class FakeDecoder implements TypedDecoder {

    @Override
    public Object decode(Response response, Type type)
        throws IOException, DecodeException, FeignException {
      return null;
    }

    @Override
    public boolean canDecode(Response response, Type type)
        throws IOException, DecodeException, FeignException {
      return false;
    }

  }

  @Test
  public void defaultDecoderWorks() throws DecodeException, FeignException, IOException {
    Response response = knownResponse();

    TypedDecoder decoder1 = new FakeDecoder();
    TypedDecoder decoder2= new FakeDecoder();
    FifoDecoder decoder = new FifoDecoder()
        .append(decoder1)
        .append(decoder2);

    Object decodedObject = decoder.decode(response, String.class);
    assertEquals(String.class, decodedObject.getClass());
    assertEquals("response body", decodedObject.toString());
  }

  private Response knownResponse() {
    String content = "response body";
    InputStream inputStream = new ByteArrayInputStream(content.getBytes(UTF_8));
    Map<String, Collection<String>> headers = new HashMap<String, Collection<String>>();
    headers.put("Content-Type", Collections.singleton("text/plain"));
    return Response.builder()
        .status(200)
        .reason("OK")
        .headers(headers)
        .request(Request.create(HttpMethod.GET, "/api", Collections.emptyMap(), null, Util.UTF_8))
        .body(inputStream, content.length())
        .build();
  }


}
