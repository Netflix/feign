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
package feign;

import static feign.assertj.FeignAssertions.assertThat;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.MapEntry.entry;
import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.CoreMatchers.startsWith;
import com.google.gson.reflect.TypeToken;
import org.assertj.core.api.Fail;
import org.hamcrest.CoreMatchers;
import org.hamcrest.Matcher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;
import java.net.URI;
import java.util.*;

/**
 * Tests interfaces defined per {@link Contract.Default} are interpreted into expected
 * {@link feign .RequestTemplate template} instances.
 */
public class DefaultContractTest {

  @Rule
  public final ExpectedException thrown = ExpectedException.none();

  Contract.Default contract = new Contract.Default();

  @Test
  public void httpMethods() throws Exception {
    assertThat(parseAndValidateMetadata(Methods.class, "post").template())
        .hasMethod("POST");

    assertThat(parseAndValidateMetadata(Methods.class, "put").template())
        .hasMethod("PUT");

    assertThat(parseAndValidateMetadata(Methods.class, "get").template())
        .hasMethod("GET");

    assertThat(parseAndValidateMetadata(Methods.class, "delete").template())
        .hasMethod("DELETE");
  }

  @Test
  public void bodyParamIsGeneric() throws Exception {
    final MethodMetadata md = parseAndValidateMetadata(BodyParams.class, "post", List.class);

    assertThat(md.bodyIndex())
        .isEqualTo(0);
    assertThat(md.bodyType())
        .isEqualTo(new TypeToken<List<String>>() {}.getType());
  }

  @Test
  public void bodyParamWithPathParam() throws Exception {
    final MethodMetadata md =
        parseAndValidateMetadata(BodyParams.class, "post", int.class, List.class);

    assertThat(md.bodyIndex())
        .isEqualTo(1);
    assertThat(md.indexToName()).containsOnly(
        entry(0, asList("id")));
  }

  @Test
  public void tooManyBodies() throws Exception {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("Method has too many Body");
    parseAndValidateMetadata(BodyParams.class, "tooMany", List.class, List.class);
  }

  @Test
  public void customMethodWithoutPath() throws Exception {
    assertThat(parseAndValidateMetadata(CustomMethod.class, "patch").template())
        .hasMethod("PATCH")
        .hasUrl("/");
  }

  @Test
  public void queryParamsInPathExtract() throws Exception {
    assertThat(parseAndValidateMetadata(WithQueryParamsInPath.class, "none").template())
        .hasUrl("/")
        .hasQueries();

    assertThat(parseAndValidateMetadata(WithQueryParamsInPath.class, "one").template())
        .hasPath("/")
        .hasQueries(
            entry("Action", asList("GetUser")));

    assertThat(parseAndValidateMetadata(WithQueryParamsInPath.class, "two").template())
        .hasPath("/")
        .hasQueries(
            entry("Action", asList("GetUser")),
            entry("Version", asList("2010-05-08")));

    assertThat(parseAndValidateMetadata(WithQueryParamsInPath.class, "three").template())
        .hasPath("/")
        .hasQueries(
            entry("Action", asList("GetUser")),
            entry("Version", asList("2010-05-08")),
            entry("limit", asList("1")));

    assertThat(parseAndValidateMetadata(WithQueryParamsInPath.class, "twoAndOneEmpty").template())
        .hasPath("/")
        .hasQueries(
            entry("flag", new ArrayList<>()),
            entry("Action", asList("GetUser")),
            entry("Version", asList("2010-05-08")));

    assertThat(parseAndValidateMetadata(WithQueryParamsInPath.class, "oneEmpty").template())
        .hasPath("/")
        .hasQueries(
            entry("flag", new ArrayList<>()));

    assertThat(parseAndValidateMetadata(WithQueryParamsInPath.class, "twoEmpty").template())
        .hasPath("/")
        .hasQueries(
            entry("flag", new ArrayList<>()),
            entry("NoErrors", new ArrayList<>()));
  }

  @Test
  public void bodyWithoutParameters() throws Exception {
    final MethodMetadata md = parseAndValidateMetadata(BodyWithoutParameters.class, "post");

    assertThat(md.template())
        .hasBody("<v01:getAccountsListOfUser/>");
  }

  @Test
  public void headersOnMethodAddsContentTypeHeader() throws Exception {
    final MethodMetadata md = parseAndValidateMetadata(BodyWithoutParameters.class, "post");

    assertThat(md.template())
        .hasHeaders(
            entry("Content-Type", asList("application/xml")),
            entry("Content-Length",
                asList(String.valueOf(md.template().body().length))));
  }

  @Test
  public void headersOnTypeAddsContentTypeHeader() throws Exception {
    final MethodMetadata md = parseAndValidateMetadata(HeadersOnType.class, "post");

    assertThat(md.template())
        .hasHeaders(
            entry("Content-Type", asList("application/xml")),
            entry("Content-Length",
                asList(String.valueOf(md.template().body().length))));
  }

  @Test
  public void headersContainsWhitespaces() throws Exception {
    final MethodMetadata md = parseAndValidateMetadata(HeadersContainsWhitespaces.class, "post");

    assertThat(md.template())
        .hasHeaders(
            entry("Content-Type", Collections.singletonList("application/xml")),
            entry("Content-Length",
                asList(String.valueOf(md.template().body().length))));
  }

  @Test
  public void withPathAndURIParam() throws Exception {
    final MethodMetadata md = parseAndValidateMetadata(WithURIParam.class,
        "uriParam", String.class, URI.class, String.class);

    assertThat(md.indexToName())
        .containsExactly(
            entry(0, asList("1")),
            // Skips 1 as it is a url index!
            entry(2, asList("2")));

    assertThat(md.urlIndex()).isEqualTo(1);
  }

  @Test
  public void pathAndQueryParams() throws Exception {
    final MethodMetadata md = parseAndValidateMetadata(WithPathAndQueryParams.class,
        "recordsByNameAndType", int.class, String.class,
        String.class);

    assertThat(md.template())
        .hasQueries(entry("name", asList("{name}")), entry("type", asList("{type}")));

    assertThat(md.indexToName()).containsExactly(
        entry(0, asList("domainId")),
        entry(1, asList("name")),
        entry(2, asList("type")));
  }

  @Test
  public void autoDiscoverParamNames() throws Exception {
    final MethodMetadata md = parseAndValidateMetadata(AutoDiscoverParamNames.class,
        "recordsByNameAndType", int.class, String.class,
        String.class);

    assertThat(md.template())
        .hasQueries(entry("name", asList("{name}")), entry("type", asList("{type}")));

    assertThat(md.indexToName()).containsExactly(
        entry(0, asList("domainId")),
        entry(1, asList("name")),
        entry(2, asList("type")));
  }

  @Test
  public void bodyWithTemplate() throws Exception {
    final MethodMetadata md = parseAndValidateMetadata(FormParams.class,
        "login", String.class, String.class, String.class);

    assertThat(md.template())
        .hasBodyTemplate(
            "%7B\"customer_name\": \"{customer_name}\", \"user_name\": \"{user_name}\", \"password\": \"{password}\"%7D");
  }

  @Test
  public void formParamsParseIntoIndexToName() throws Exception {
    final MethodMetadata md = parseAndValidateMetadata(FormParams.class,
        "login", String.class, String.class, String.class);

    assertThat(md.formParams())
        .containsExactly("customer_name", "user_name", "password");

    assertThat(md.indexToName()).containsExactly(
        entry(0, asList("customer_name")),
        entry(1, asList("user_name")),
        entry(2, asList("password")));
  }

  @Test
  public void formParamAndBodyParams() throws Exception {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("Body parameters cannot be used with form parameters.");

    parseAndValidateMetadata(FormParams.class,
        "formParamAndBodyParams", String.class, String.class);
    Fail.failBecauseExceptionWasNotThrown(IllegalStateException.class);
  }

  @Test
  public void bodyParamsAndformParam() throws Exception {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("Body parameters cannot be used with form parameters.");

    parseAndValidateMetadata(FormParams.class,
        "bodyParamsAndformParam", String.class, String.class);
    Fail.failBecauseExceptionWasNotThrown(IllegalStateException.class);
  }

  @Test
  public void formParamParseIntoFormParams() throws Exception {

    MethodMetadata md = parseAndValidateMetadata(FormParams.class,
        "loginNoBodyTemplate", String.class, String.class, String.class);

    assertThat(md.formParams())
        .containsExactly("customer_name", "user_name", "password");

    assertThat(md.indexToName()).containsExactly(
        entry(0, asList("customer_name")),
        entry(1, asList("user_name")),
        entry(2, asList("password")));
  }


  /**
   * Body type is only for the body param.
   */
  @Test
  public void formParamsDoesNotSetBodyType() throws Exception {
    final MethodMetadata md = parseAndValidateMetadata(FormParams.class,
        "login", String.class, String.class, String.class);

    assertThat(md.bodyType()).isNull();
  }

  @Test
  public void headerParamsParseIntoIndexToName() throws Exception {
    final MethodMetadata md = parseAndValidateMetadata(HeaderParams.class, "logout", String.class);

    assertThat(md.template())
        .hasHeaders(entry("Auth-Token", asList("{authToken}", "Foo")));

    assertThat(md.indexToName())
        .containsExactly(entry(0, asList("authToken")));
    assertThat(md.formParams()).isEmpty();
  }

  @Test
  public void headerParamsParseIntoIndexToNameNotAtStart() throws Exception {
    final MethodMetadata md =
        parseAndValidateMetadata(HeaderParamsNotAtStart.class, "logout", String.class);

    assertThat(md.template())
        .hasHeaders(entry("Authorization", asList("Bearer {authToken}", "Foo")));

    assertThat(md.indexToName())
        .containsExactly(entry(0, asList("authToken")));
    assertThat(md.formParams()).isEmpty();
  }

  @Test
  public void customExpander() throws Exception {
    final MethodMetadata md = parseAndValidateMetadata(CustomExpander.class, "date", Date.class);

    assertThat(md.indexToExpanderClass())
        .containsExactly(entry(0, DateToMillis.class));
  }

  @Test
  public void queryMap() throws Exception {
    final MethodMetadata md =
        parseAndValidateMetadata(QueryMapTestInterface.class, "queryMap", Map.class);

    assertThat(md.queryMapIndex()).isEqualTo(0);
  }

  @Test
  public void queryMapEncodedDefault() throws Exception {
    final MethodMetadata md =
        parseAndValidateMetadata(QueryMapTestInterface.class, "queryMap", Map.class);

    assertThat(md.queryMapEncoded()).isFalse();
  }

  @Test
  public void queryMapEncodedTrue() throws Exception {
    final MethodMetadata md =
        parseAndValidateMetadata(QueryMapTestInterface.class, "queryMapEncoded", Map.class);

    assertThat(md.queryMapEncoded()).isTrue();
  }

  @Test
  public void queryMapEncodedFalse() throws Exception {
    final MethodMetadata md =
        parseAndValidateMetadata(QueryMapTestInterface.class, "queryMapNotEncoded", Map.class);

    assertThat(md.queryMapEncoded()).isFalse();
  }

  @Test
  public void queryMapMapSubclass() throws Exception {
    final MethodMetadata md =
        parseAndValidateMetadata(QueryMapTestInterface.class, "queryMapMapSubclass",
            SortedMap.class);

    assertThat(md.queryMapIndex()).isEqualTo(0);
  }

  @Test
  public void onlyOneQueryMapAnnotationPermitted() throws Exception {
    try {
      parseAndValidateMetadata(QueryMapTestInterface.class, "multipleQueryMap", Map.class,
          Map.class);
      Fail.failBecauseExceptionWasNotThrown(IllegalStateException.class);
    } catch (final IllegalStateException ex) {
      assertThat(ex).hasMessage("QueryMap annotation was present on multiple parameters.");
    }
  }

  @Test
  public void queryMapKeysMustBeStrings() throws Exception {
    try {
      parseAndValidateMetadata(QueryMapTestInterface.class, "nonStringKeyQueryMap", Map.class);
      Fail.failBecauseExceptionWasNotThrown(IllegalStateException.class);
    } catch (final IllegalStateException ex) {
      assertThat(ex).hasMessage("QueryMap key must be a String: Integer");
    }
  }

  @Test
  public void queryMapPojoObject() throws Exception {
    final MethodMetadata md =
        parseAndValidateMetadata(QueryMapTestInterface.class, "pojoObject", Object.class);

    assertThat(md.queryMapIndex()).isEqualTo(0);
  }

  @Test
  public void queryMapPojoObjectEncoded() throws Exception {
    final MethodMetadata md =
        parseAndValidateMetadata(QueryMapTestInterface.class, "pojoObjectEncoded", Object.class);

    assertThat(md.queryMapIndex()).isEqualTo(0);
    assertThat(md.queryMapEncoded()).isTrue();
  }

  @Test
  public void queryMapPojoObjectNotEncoded() throws Exception {
    final MethodMetadata md =
        parseAndValidateMetadata(QueryMapTestInterface.class, "pojoObjectNotEncoded", Object.class);

    assertThat(md.queryMapIndex()).isEqualTo(0);
    assertThat(md.queryMapEncoded()).isFalse();
  }

  @Test
  public void slashAreEncodedWhenNeeded() throws Exception {
    MethodMetadata md = parseAndValidateMetadata(SlashNeedToBeEncoded.class,
        "getQueues", String.class);

    assertThat(md.template().decodeSlash()).isFalse();

    md = parseAndValidateMetadata(SlashNeedToBeEncoded.class, "getZone", String.class);

    assertThat(md.template().decodeSlash()).isTrue();
  }

  @Test
  public void onlyOneHeaderMapAnnotationPermitted() throws Exception {
    try {
      parseAndValidateMetadata(HeaderMapInterface.class, "multipleHeaderMap", Map.class, Map.class);
      Fail.failBecauseExceptionWasNotThrown(IllegalStateException.class);
    } catch (final IllegalStateException ex) {
      assertThat(ex).hasMessage("HeaderMap annotation was present on multiple parameters.");
    }
  }

  @Test
  public void headerMapSubclass() throws Exception {
    final MethodMetadata md =
        parseAndValidateMetadata(HeaderMapInterface.class, "headerMapSubClass",
            SubClassHeaders.class);
    assertThat(md.headerMapIndex()).isEqualTo(0);
  }

  interface Methods {

    @RequestLine("POST /")
    void post();

    @RequestLine("PUT /")
    void put();

    @RequestLine("GET /")
    void get();

    @RequestLine("DELETE /")
    void delete();
  }

  interface BodyParams {

    @RequestLine("POST")
    Response post(List<String> body);

    @RequestLine("PUT /offers/{id}")
    void post(@Param("id") int id, List<String> body);

    @RequestLine("POST")
    Response tooMany(List<String> body, List<String> body2);
  }

  interface CustomMethod {

    @RequestLine("PATCH")
    Response patch();
  }

  interface WithQueryParamsInPath {

    @RequestLine("GET /")
    Response none();

    @RequestLine("GET /?Action=GetUser")
    Response one();

    @RequestLine("GET /?Action=GetUser&Version=2010-05-08")
    Response two();

    @RequestLine("GET /?Action=GetUser&Version=2010-05-08&limit=1")
    Response three();

    @RequestLine("GET /?flag&Action=GetUser&Version=2010-05-08")
    Response twoAndOneEmpty();

    @RequestLine("GET /?flag")
    Response oneEmpty();

    @RequestLine("GET /?flag&NoErrors")
    Response twoEmpty();
  }

  interface BodyWithoutParameters {

    @RequestLine("POST /")
    @Headers("Content-Type: application/xml")
    @Body("<v01:getAccountsListOfUser/>")
    Response post();
  }

  @Headers("Content-Type: application/xml")
  interface HeadersOnType {

    @RequestLine("POST /")
    @Body("<v01:getAccountsListOfUser/>")
    Response post();
  }

  @Headers("Content-Type:    application/xml   ")
  interface HeadersContainsWhitespaces {

    @RequestLine("POST /")
    @Body("<v01:getAccountsListOfUser/>")
    Response post();
  }

  interface WithURIParam {

    @RequestLine("GET /{1}/{2}")
    Response uriParam(@Param("1") String one, URI endpoint, @Param("2") String two);
  }

  interface WithPathAndQueryParams {

    @RequestLine("GET /domains/{domainId}/records?name={name}&type={type}")
    Response recordsByNameAndType(@Param("domainId") int id,
                                  @Param("name") String nameFilter,
                                  @Param("type") String typeFilter);
  }

  interface AutoDiscoverParamNames {

    @RequestLine("GET /domains/{domainId}/records?name={name}&type={type}")
    Response recordsByNameAndType(@Param int domainId, @Param String name, @Param() String type);
  }

  interface FormParams {

    @RequestLine("POST /")
    @Body("%7B\"customer_name\": \"{customer_name}\", \"user_name\": \"{user_name}\", \"password\": \"{password}\"%7D")
    void login(
               @Param("customer_name") String customer,
               @Param("user_name") String user,
               @Param("password") String password);

    @RequestLine("POST /")
    void loginNoBodyTemplate(
                             @Param("customer_name") String customer,
                             @Param("user_name") String user,
                             @Param("password") String password);

    @RequestLine("POST /")
    void formParamAndBodyParams(
                                @Param("customer_name") String customer,
                                String body);

    @RequestLine("POST /")
    void bodyParamsAndformParam(
                                String body,
                                @Param("customer_name") String customer);
  }

  interface HeaderMapInterface {

    @RequestLine("POST /")
    void multipleHeaderMap(@HeaderMap Map<String, String> headers,
                           @HeaderMap Map<String, String> queries);

    @RequestLine("POST /")
    void headerMapSubClass(@HeaderMap SubClassHeaders httpHeaders);
  }

  interface HeaderParams {

    @RequestLine("POST /")
    @Headers({"Auth-Token: {authToken}", "Auth-Token: Foo"})
    void logout(@Param("authToken") String token);
  }

  interface HeaderParamsNotAtStart {

    @RequestLine("POST /")
    @Headers({"Authorization: Bearer {authToken}", "Authorization: Foo"})
    void logout(@Param("authToken") String token);
  }

  interface CustomExpander {

    @RequestLine("POST /?date={date}")
    void date(@Param(value = "date", expander = DateToMillis.class) Date date);
  }

  class DateToMillis implements Param.Expander {

    @Override
    public String expand(Object value) {
      return String.valueOf(((Date) value).getTime());
    }
  }

  interface QueryMapTestInterface {

    @RequestLine("POST /")
    void queryMap(@QueryMap Map<String, String> queryMap);

    @RequestLine("POST /")
    void queryMapMapSubclass(@QueryMap SortedMap<String, String> queryMap);

    @RequestLine("POST /")
    void queryMapEncoded(@QueryMap(encoded = true) Map<String, String> queryMap);

    @RequestLine("POST /")
    void queryMapNotEncoded(@QueryMap(encoded = false) Map<String, String> queryMap);

    @RequestLine("POST /")
    void pojoObject(@QueryMap Object object);

    @RequestLine("POST /")
    void pojoObjectEncoded(@QueryMap(encoded = true) Object object);

    @RequestLine("POST /")
    void pojoObjectNotEncoded(@QueryMap(encoded = false) Object object);

    // invalid
    @RequestLine("POST /")
    void multipleQueryMap(@QueryMap Map<String, String> mapOne,
                          @QueryMap Map<String, String> mapTwo);

    // invalid
    @RequestLine("POST /")
    void nonStringKeyQueryMap(@QueryMap Map<Integer, String> queryMap);
  }

  interface SlashNeedToBeEncoded {
    @RequestLine(value = "GET /api/queues/{vhost}", decodeSlash = false)
    String getQueues(@Param("vhost") String vhost);

    @RequestLine(value = "GET /api/{zoneId}")
    String getZone(@Param("ZoneId") String vhost);
  }

  @Headers("Foo: Bar")
  interface SimpleParameterizedBaseApi<M> {

    @RequestLine("GET /api/{zoneId}")
    M get(@Param("key") String key);
  }

  interface SimpleParameterizedApi extends SimpleParameterizedBaseApi<String> {

  }

  @Test
  public void simpleParameterizedBaseApi() throws Exception {
    final List<MethodMetadata> md = contract.parseAndValidateMetadata(SimpleParameterizedApi.class);

    assertThat(md).hasSize(1);

    assertThat(md.get(0).configKey())
        .isEqualTo("SimpleParameterizedApi#get(String)");
    assertThat(md.get(0).returnType())
        .isEqualTo(String.class);
    assertThat(md.get(0).template())
        .hasHeaders(entry("Foo", asList("Bar")));
  }

  @Test
  public void parameterizedApiUnsupported() throws Exception {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("Parameterized types unsupported: SimpleParameterizedBaseApi");
    contract.parseAndValidateMetadata(SimpleParameterizedBaseApi.class);
  }

  interface OverrideParameterizedApi extends SimpleParameterizedBaseApi<String> {

    @Override
    @RequestLine("GET /api/{zoneId}")
    String get(@Param("key") String key);
  }

  @Test
  public void overrideBaseApiUnsupported() throws Exception {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("Overrides unsupported: OverrideParameterizedApi#get(String)");
    contract.parseAndValidateMetadata(OverrideParameterizedApi.class);
  }

  interface Child<T> extends SimpleParameterizedBaseApi<List<T>> {

  }

  interface GrandChild extends Child<String> {

  }

  @Test
  public void onlySingleLevelInheritanceSupported() throws Exception {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("Only single-level inheritance supported: GrandChild");
    contract.parseAndValidateMetadata(GrandChild.class);
  }

  @Headers("Foo: Bar")
  interface ParameterizedBaseApi<K, M> {

    @RequestLine("GET /api/{key}")
    Entity<K, M> get(@Param("key") K key);

    @RequestLine("POST /api")
    Entities<K, M> getAll(Keys<K> keys);
  }

  static class Keys<K> {

    List<K> keys;
  }

  static class Entity<K, M> {

    K key;
    M model;
  }

  static class Entities<K, M> {

    private List<Entity<K, M>> entities;
  }


  interface SubClassHeaders extends Map<String, String> {

  }


  @Headers("Version: 1")
  interface ParameterizedApi extends ParameterizedBaseApi<String, Long> {

  }

  @Test
  public void parameterizedBaseApi() throws Exception {
    final List<MethodMetadata> md = contract.parseAndValidateMetadata(ParameterizedApi.class);

    final Map<String, MethodMetadata> byConfigKey = new LinkedHashMap<String, MethodMetadata>();
    for (final MethodMetadata m : md) {
      byConfigKey.put(m.configKey(), m);
    }

    assertThat(byConfigKey)
        .containsOnlyKeys("ParameterizedApi#get(String)", "ParameterizedApi#getAll(Keys)");

    assertThat(byConfigKey.get("ParameterizedApi#get(String)").returnType())
        .isEqualTo(new TypeToken<Entity<String, Long>>() {}.getType());
    assertThat(byConfigKey.get("ParameterizedApi#get(String)").template()).hasHeaders(
        entry("Version", asList("1")),
        entry("Foo", asList("Bar")));

    assertThat(byConfigKey.get("ParameterizedApi#getAll(Keys)").returnType())
        .isEqualTo(new TypeToken<Entities<String, Long>>() {}.getType());
    assertThat(byConfigKey.get("ParameterizedApi#getAll(Keys)").bodyType())
        .isEqualTo(new TypeToken<Keys<String>>() {}.getType());
    assertThat(byConfigKey.get("ParameterizedApi#getAll(Keys)").template()).hasHeaders(
        entry("Version", asList("1")),
        entry("Foo", asList("Bar")));
  }

  @Headers("Authorization: {authHdr}")
  interface ParameterizedHeaderExpandApi {
    @RequestLine("GET /api/{zoneId}")
    @Headers("Accept: application/json")
    String getZone(@Param("zoneId") String vhost, @Param("authHdr") String authHdr);
  }

  @Test
  public void parameterizedHeaderExpandApi() throws Exception {
    final List<MethodMetadata> md =
        contract.parseAndValidateMetadata(ParameterizedHeaderExpandApi.class);

    assertThat(md).hasSize(1);

    assertThat(md.get(0).configKey())
        .isEqualTo("ParameterizedHeaderExpandApi#getZone(String,String)");
    assertThat(md.get(0).returnType())
        .isEqualTo(String.class);
    assertThat(md.get(0).template())
        .hasHeaders(entry("Authorization", asList("{authHdr}")),
            entry("Accept", asList("application/json")));
    // Ensure that the authHdr expansion was properly detected and did not create a formParam
    assertThat(md.get(0).formParams())
        .isEmpty();
  }

  @Test
  public void parameterizedHeaderNotStartingWithCurlyBraceExpandApi() throws Exception {
    final List<MethodMetadata> md =
        contract.parseAndValidateMetadata(
            ParameterizedHeaderNotStartingWithCurlyBraceExpandApi.class);

    assertThat(md).hasSize(1);

    assertThat(md.get(0).configKey())
        .isEqualTo("ParameterizedHeaderNotStartingWithCurlyBraceExpandApi#getZone(String,String)");
    assertThat(md.get(0).returnType())
        .isEqualTo(String.class);
    assertThat(md.get(0).template())
        .hasHeaders(entry("Authorization", asList("Bearer {authHdr}")),
            entry("Accept", asList("application/json")));
    // Ensure that the authHdr expansion was properly detected and did not create a formParam
    assertThat(md.get(0).formParams())
        .isEmpty();
  }

  @Headers("Authorization: Bearer {authHdr}")
  interface ParameterizedHeaderNotStartingWithCurlyBraceExpandApi {
    @RequestLine("GET /api/{zoneId}")
    @Headers("Accept: application/json")
    String getZone(@Param("zoneId") String vhost, @Param("authHdr") String authHdr);
  }

  @Headers("Authorization: {authHdr}")
  interface ParameterizedHeaderBase {
  }

  interface ParameterizedHeaderExpandInheritedApi extends ParameterizedHeaderBase {
    @RequestLine("GET /api/{zoneId}")
    @Headers("Accept: application/json")
    String getZoneAccept(@Param("zoneId") String vhost, @Param("authHdr") String authHdr);

    @RequestLine("GET /api/{zoneId}")
    String getZone(@Param("zoneId") String vhost, @Param("authHdr") String authHdr);
  }

  @Test
  public void parameterizedHeaderExpandApiBaseClass() throws Exception {
    final List<MethodMetadata> mds =
        contract.parseAndValidateMetadata(ParameterizedHeaderExpandInheritedApi.class);

    final Map<String, MethodMetadata> byConfigKey = new LinkedHashMap<String, MethodMetadata>();
    for (final MethodMetadata m : mds) {
      byConfigKey.put(m.configKey(), m);
    }

    assertThat(byConfigKey)
        .containsOnlyKeys("ParameterizedHeaderExpandInheritedApi#getZoneAccept(String,String)",
            "ParameterizedHeaderExpandInheritedApi#getZone(String,String)");

    MethodMetadata md =
        byConfigKey.get("ParameterizedHeaderExpandInheritedApi#getZoneAccept(String,String)");
    assertThat(md.returnType())
        .isEqualTo(String.class);
    assertThat(md.template())
        .hasHeaders(entry("Authorization", asList("{authHdr}")),
            entry("Accept", asList("application/json")));
    // Ensure that the authHdr expansion was properly detected and did not create a formParam
    assertThat(md.formParams())
        .isEmpty();

    md = byConfigKey.get("ParameterizedHeaderExpandInheritedApi#getZone(String,String)");
    assertThat(md.returnType())
        .isEqualTo(String.class);
    assertThat(md.template())
        .hasHeaders(entry("Authorization", asList("{authHdr}")));
    assertThat(md.formParams())
        .isEmpty();
  }

  private MethodMetadata parseAndValidateMetadata(Class<?> targetType,
                                                  String method,
                                                  Class<?>... parameterTypes)
      throws NoSuchMethodException {
    return contract.parseAndValidateMetadata(targetType,
        targetType.getMethod(method, parameterTypes));
  }

  interface MissingMethod {
    @RequestLine("/path?queryParam={queryParam}")
    Response updateSharing(@Param("queryParam") long queryParam, String bodyParam);
  }

  /** Let's help folks not lose time when they mistake request line for a URI! */
  @Test
  public void missingMethod() throws Exception {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage(
        "RequestLine annotation didn't start with an HTTP verb on method MissingMethod#updateSharing");

    contract.parseAndValidateMetadata(MissingMethod.class);
  }

  interface StaticMethodOnInterface {
    @RequestLine("GET /api/{key}")
    String get(@Param("key") String key);

    static String staticMethod() {
      return "value";
    }
  }

  @Test
  public void staticMethodsOnInterfaceIgnored() throws Exception {
    final List<MethodMetadata> mds =
        contract.parseAndValidateMetadata(StaticMethodOnInterface.class);
    assertThat(mds).hasSize(1);
    final MethodMetadata md = mds.get(0);
    assertThat(md.configKey()).isEqualTo("StaticMethodOnInterface#get(String)");
  }

  interface DefaultMethodOnInterface {
    @RequestLine("GET /api/{key}")
    String get(@Param("key") String key);

    default String defaultGet(String key) {
      return get(key);
    }
  }

  @Test
  public void defaultMethodsOnInterfaceIgnored() throws Exception {
    final List<MethodMetadata> mds =
        contract.parseAndValidateMetadata(DefaultMethodOnInterface.class);
    assertThat(mds).hasSize(1);
    final MethodMetadata md = mds.get(0);
    assertThat(md.configKey()).isEqualTo("DefaultMethodOnInterface#get(String)");
  }

  interface SubstringQuery {
    @RequestLine("GET /_search?q=body:{body}")
    String paramIsASubstringOfAQuery(@Param("body") String body);
  }

  @Test
  public void paramIsASubstringOfAQuery() throws Exception {
    final List<MethodMetadata> mds = contract.parseAndValidateMetadata(SubstringQuery.class);

    assertThat(mds.get(0).template().queries()).containsExactly(
        entry("q", asList("body:{body}")));
    assertThat(mds.get(0).formParams()).isEmpty(); // Prevent issue 424
  }

  @Test
  public void errorMessageOnMixedContracts() {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("are not used by contract Default");

    contract.parseAndValidateMetadata(MixedAnnotations.class);
  }

  interface MixedAnnotations {
    @Headers("Content-Type: application/json")
    @RequestLine("GET api/v2/clients/{uid}")
    Response findAllClientsByUid2(@Category(value = String.class) String uid,
                                  Integer limit,
                                  @SuppressWarnings({"a"}) Integer offset);
  }

  @Test
  public void errorOnSharedMethodWithUnannotatedParameters() {
    thrown.expect(IllegalStateException.class);
    thrown
        .expectMessage(startsWith("Param annotation was not present on some parameters of method"));

    contract.parseAndValidateMetadata(SharedMethodWithUnannotatedParameters.class);
  }

  @Test
  public void errorOnSharedMethodWithUnexpectedReturnType() {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage(startsWith("Shared method"));
    thrown.expectMessage(endsWith("return type must be its own interface"));

    contract.parseAndValidateMetadata(SharedMethodWithVoidReturnType.class);
  }

  interface SharedMethodWithUnannotatedParameters {
    @Shared
    SharedMethodWithUnannotatedParameters configure(String param);
  }

  interface SharedMethodWithVoidReturnType {
    @Shared
    void configure(@Param("param") String param);
  }
}
