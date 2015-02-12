package feign.ribbon;

import java.io.IOException;
import java.net.URI;

import com.netflix.client.ClientException;
import com.netflix.client.ClientFactory;
import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.client.config.IClientConfig;
import com.netflix.loadbalancer.ILoadBalancer;

import feign.Client;
import feign.Request;
import feign.Response;

/**
 * RibbonClient can be used in Fiegn builder to activate smart routing and resiliency capabilities
 * provided by Ribbon. Ex.
 * <pre>
 * MyService api = Feign.builder.client(new RibbonClient()).target(MyService.class,
 * "http://myAppProd");
 * </pre>
 * Where {@code myAppProd} is the ribbon client name and {@code myAppProd.ribbon.listOfServers}
 * configuration is set.
 */
public class RibbonClient implements Client {

  private final Client delegate;

  public RibbonClient() {
    this.delegate = new Client.Default(null, null);
  }

  public RibbonClient(Client delegate) {
    this.delegate = delegate;
  }

  @Override
  public Response execute(Request request, Request.Options options) throws IOException {
    try {
      URI asUri = URI.create(request.url());
      String clientName = asUri.getHost();
      URI uriWithoutHost = URI.create(request.url().replace(asUri.getHost(), ""));
      LBClient.RibbonRequest ribbonRequest = new LBClient.RibbonRequest(request, uriWithoutHost);
      return lbClient(clientName).executeWithLoadBalancer(ribbonRequest,
          new FeignOptionsClientConfig(options)).toResponse();
    } catch (ClientException e) {
      if (e.getCause() instanceof IOException) {
        throw IOException.class.cast(e.getCause());
      }
      throw new RuntimeException(e);
    }
  }

  private LBClient lbClient(String clientName) {
    IClientConfig config = ClientFactory.getNamedConfig(clientName);
    ILoadBalancer lb = ClientFactory.getNamedLoadBalancer(clientName);
    return new LBClient(delegate, lb, config);
  }
  
  static class FeignOptionsClientConfig extends DefaultClientConfigImpl {

    public FeignOptionsClientConfig(Request.Options options) {
      setProperty(CommonClientConfigKey.ConnectTimeout, options.connectTimeoutMillis());
      setProperty(CommonClientConfigKey.ReadTimeout, options.readTimeoutMillis());
    }

    @Override
    public void loadProperties(String clientName) {

    }

    @Override
    public void loadDefaultValues() {

    }

  }
  
}
