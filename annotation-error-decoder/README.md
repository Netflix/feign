Annotation Error Decoder
=========================

This module allows to annotate Feign's interfaces with annotations to generate Exceptions based on error codes

To use AnnotationErrorDecoder with Feign, add the Annotation Error Decoder module to your classpath. Then, configure
Feign to use the AnnotationErrorDecoder:

```java
GitHub github = Feign.builder()
                     .errorDecoder(
                         AnnotationErrorDecoder.builderFor(GitHub.class).build()
                     )
                     .target(GitHub.class, "https://api.github.com");
```

## Leveraging the annotations and priority order
For annotation decoding to work, the class must be annotated with `@ErrorHandling` tags or meta-annotations.
The tags are valid in both the class level as well as method level. They will be treated from 'most specific' to 
'least specific' in the following order:
* A code specific exception defined on the method 
* A code specific exception defined on the class
* The default exception of the method
* The default exception of the class

```java
@ErrorHandling(codeSpecific =
    {
        @ErrorCodes( codes = {401}, generate = UnAuthorizedException.class),
        @ErrorCodes( codes = {403}, generate = ForbiddenException.class),
        @ErrorCodes( codes = {404}, generate = UnknownItemException.class),
    },
    defaultException = ClassLevelDefaultException.class
)
interface GitHub {

    @ErrorHandling(codeSpecific =
        {
            @ErrorCodes( codes = {404}, generate = NonExistentRepoException.class),
            @ErrorCodes( codes = {502, 503, 504}, generate = RetryAfterCertainTimeException.class),
        },
        defaultException = FailedToGetContributorsException.class
    )
    @RequestLine("GET /repos/{owner}/{repo}/contributors")
    List<Contributor> contributors(@Param("owner") String owner, @Param("repo") String repo);
}
```
In the above example, error responses to 'contributors' would hence be mapped as follows by status codes:

| Code        | Exception                          | Reason                |
| ----------- | --------------------------------   | --------------------- |
| 401         | `UnAuthorizedException`            | from Class definition |
| 403         | `ForbiddenException`               | from Class definition |
| 404         | `NonExistenRepoException`          | from Method definition, note that the class generic exception won't be thrown here |
| 502,503,504 | `RetryAfterCertainTimeException`   | from method definition. Note that you can have multiple error codes generate the same type of exception |
| Any Other   | `FailedToGetContributorsException` | from Method default   |

For a class level default exception to be thrown, the method must not have a `defaultException` defined, nor must the error code
be mapped at either the method or class level.

If the return code cannot be mapped to any code and no default exceptions have been configured, then the decoder will
drop to a default decoder (by default, the standard one provided by feign). You can change the default drop-into decoder
as follows:

```java
GitHub github = Feign.builder()
                     .errorDecoder(
                         AnnotationErrorDecoder.builderFor(GitHub.class)
                            .withDefaultDecoder(new MyOtherErrorDecoder())
                            .build()
                     )
                     .target(GitHub.class, "https://api.github.com");
```

 
## Complex Exceptions

Any exception can be used if they have a default constructor:

```java
class DefaultConstructorException extends Exception {}
```

However, if you want to have parameters (such  as the feign.Request object or response body or response headers), you have to annotate its 
constructor appropriately (the body annotation is optional, provided there aren't paramters which will clash)

All the following examples are valid exceptions:
```java
class JustBody extends Exception {

    @FeignExceptionConstructor
    public JustBody(String body) {

    }
}
class JustRequest extends Exception {

    @FeignExceptionConstructor
    public JustRequest(Request request) {

    }
}
class RequestAndResponseBody extends Exception {

    @FeignExceptionConstructor
    public RequestAndResponseBody(Request request, String body) {

    }
}
//Headers must be of type Map<String, Collection<String>>
class BodyAndHeaders extends Exception {

    @FeignExceptionConstructor
    public BodyAndHeaders(@ResponseBody String body, @ResponseHeaders Map<String, Collection<String>> headers) {

    }
}
class RequestAndResponseBodyAndHeaders extends Exception {

    @FeignExceptionConstructor
    public RequestAndResponseBodyAndHeaders(Request request, @ResponseBody String body, @ResponseHeaders Map<String, Collection<String>> headers) {

    }
}
class JustHeaders extends Exception {

    @FeignExceptionConstructor
    public JustHeaders(@ResponseHeaders Map<String, Collection<String>> headers) {

    }
}
```

If you want to have the body decoded, you'll need to pass a decoder at construction time (just as for normal responses):

```java
GitHub github = Feign.builder()
                     .errorDecoder(
                         AnnotationErrorDecoder.builderFor(GitHub.class)
                            .withResponseBodyDecoder(new JacksonDecoder())
                            .build()
                     )
                     .target(GitHub.class, "https://api.github.com");
```

This will enable you to create exceptions where the body is a complex pojo:

```java
class ComplexPojoException extends Exception {

    @FeignExceptionConstructor
    public ComplexPojoException(GithubExceptionResponse body) {
        if (body != null) {
            // extract data
        } else {
            // fallback code
        }
    }
}
//The pojo can then be anything you'd like provided the decoder can manage it
class GithubExceptionResponse {
    public String message;
    public int githubCode;
    public List<String> urlsForHelp;
}
```

It's worth noting that at setup/startup time, the generators are checked with a null value of the body.
If you don't do the null-checker, you'll get an NPE and startup will fail.


## Inheriting from other interface definitions
You can create a client interface that inherits from a different one. However, there are some limitations that
you should be aware of (for most cases, these shouldn't be an issue):
* The inheritance is not natural java inheritance of annotations - as these don't work on interfaces
* Instead, the error looks at the class and if it finds the `@ErrorHandling` annotation, it uses that one.
* If not, it will look at *all* the interfaces the main interface `extends` - but it does so in the order the
java API gives it - so order is not guaranteed.
* If it finds the annotation in one of those parents, it uses that definition, without looking at any other
* That means that if more than one interface was extended which contained the `@ErrorHandling` annotation, we can't
really guarantee which one of the parents will be selected and you should really do handling at the child interface
  * so far, the java API seems to return in order of definition after the `extends`, but it's a really bad practice
  if you have to depend on that... so our suggestion: don't.

That means that as long as you only ever extend from a base interface (where you may decide that all 404's are "NotFoundException", for example)
then you should be ok. But if you get complex in polymorphism, all bets are off - so don't go crazy!

Example:
In the following code:
* The base `FeignClientBase` interface defines a default set of exceptions at class level
* the `GitHub1` and `GitHub2` interfaces will inherit the class-level error handling, which means that
any 401/403/404 will be handled correctly (provided the method doesn't specify a more specific exception)
* the `GitHub3` interface however, by defining its own error handling, will handle all 401's, but not the
403/404's since there's no merging/etc (not really in the plan to implement either...)
```java

@ErrorHandling(codeSpecific =
    {
        @ErrorCodes( codes = {401}, generate = UnAuthorizedException.class),
        @ErrorCodes( codes = {403}, generate = ForbiddenException.class),
        @ErrorCodes( codes = {404}, generate = UnknownItemException.class),
    },
    defaultException = ClassLevelDefaultException.class
)
interface FeignClientBase {}

interface GitHub1 extends FeignClientBase {

    @ErrorHandling(codeSpecific =
        {
            @ErrorCodes( codes = {404}, generate = NonExistentRepoException.class),
            @ErrorCodes( codes = {502, 503, 504}, generate = RetryAfterCertainTimeException.class),
        },
        defaultException = FailedToGetContributorsException.class
    )
    @RequestLine("GET /repos/{owner}/{repo}/contributors")
    List<Contributor> contributors(@Param("owner") String owner, @Param("repo") String repo);
}

interface GitHub2 extends FeignClientBase {

    @ErrorHandling(codeSpecific =
        {
            @ErrorCodes( codes = {404}, generate = NonExistentRepoException.class),
            @ErrorCodes( codes = {502, 503, 504}, generate = RetryAfterCertainTimeException.class),
        },
        defaultException = FailedToGetContributorsException.class
    )
    @RequestLine("GET /repos/{owner}/{repo}/contributors")
    List<Contributor> contributors(@Param("owner") String owner, @Param("repo") String repo);
}

@ErrorHandling(codeSpecific =
    {
        @ErrorCodes( codes = {401}, generate = UnAuthorizedException.class)
    },
    defaultException = ClassLevelDefaultException.class
)
interface GitHub3 extends FeignClientBase {

    @ErrorHandling(codeSpecific =
        {
            @ErrorCodes( codes = {404}, generate = NonExistentRepoException.class),
            @ErrorCodes( codes = {502, 503, 504}, generate = RetryAfterCertainTimeException.class),
        },
        defaultException = FailedToGetContributorsException.class
    )
    @RequestLine("GET /repos/{owner}/{repo}/contributors")
    List<Contributor> contributors(@Param("owner") String owner, @Param("repo") String repo);
}
```

## Meta-annotations
When you want to share the same configuration of one `@ErrorHandling` annotation the `@ErrorHandling` annotation 
can be moved to a meta-annotation. Then later on this meta-annotation can be used on a method or at class level to 
reduce the amount duplicated code. A meta-annotation is a special annotation that contains the `@ErrorHandling` 
annotation and possibly other annotations, e.g. Spring-Rest annotations.

There are some limitations and rules to keep in mind when using meta-annotation:
- inheritance for meta-annotations when using interface inheritance is supported and is following the same rules as for
  interface inheritance (see above)
  - `@ErrorHandling` has **precedence** over any meta-annotation when placed together on a class or method
  - a meta-annotation on a child interface (method or class) has **precedence** over the error handling defined in the 
    parent interface
- having a meta-annotation on a meta-annotation is not supported, only the annotations on a type are checked for a 
  `@ErrorHandling`
- when multiple meta-annotations with an `@ErrorHandling` annotation are present on a class or method the first one
  which is returned by java API is used to figure out the error handling, the others are not considered, so it is
  advisable to have only one meta-annotation on each method or class as the order is not guaranteed. 
- **no merging** of configurations is supported, e.g. multiple meta-annotations on the same type, meta-annotation with 
  `@ErrorHandling` on the same type

Example:

Let's assume multiple methods need to handle the response-code `404` in the same way but differently what is 
specified in the `@ErrorHandling` annotation on the class-level. In that case, to avoid also duplicate annotation definitions
on the affected methods a meta-annotation can reduce the amount of code to be written to handle this `404` differently.

In the following code the status-code `404` is handled on a class level which throws an `UnknownItemException` for all
methods inside this interface. For the methods `contributors` and `languages` a different exceptions needs to be thrown,
in this case it is a `NoDataFoundException`. The `teams`method will still use the exception defined by the class-level
error handling annotation. To simplify the code a meta-annotation can be created and be used in the interface to keep 
the interface small and readable.

```java
@ErrorHandling(
    codeSpecific = {
        @ErrorCodes(codes = {404}, generate = NoDataFoundException.class),
    },
    defaultException = GithubRemoteException.class)
@Retention(RetentionPolicy.RUNTIME)
@interface NoDataErrorHandling {
}
```

Having this meta-annotation in place it can be used to transform the interface into a much smaller one, keeping the same
behavior.
- `contributers` will throw a `NoDataFoundException` for status code `404` as defined on method level and a 
  `GithubRemoteException` for all other status codes
- `languages` will throw  a `NoDataFoundException` for status code `404` as defined on method level and a 
  `GithubRemoteException` for all other status codes
- `teams` will throw  a `UnknownItemException` for status code `404` as defined on class level and a  
  `ClassLevelDefaultException` for all other status codes

Before:
```java
@ErrorHandling(codeSpecific =
    {
        @ErrorCodes( codes = {404}, generate = UnknownItemException.class)
    },
    defaultException = ClassLevelDefaultException.class
)
interface GitHub {
    @ErrorHandling(codeSpecific =
        {
            @ErrorCodes( codes = {404}, generate = NoDataFoundException.class)
        },
        defaultException = GithubRemoteException.class
    )
    @RequestLine("GET /repos/{owner}/{repo}/contributors")
    List<Contributor> contributors(@Param("owner") String owner, @Param("repo") String repo);

    @ErrorHandling(codeSpecific =
        {
            @ErrorCodes( codes = {404}, generate = NoDataFoundException.class)
        },
        defaultException = GithubRemoteException.class
    )
    @RequestLine("GET /repos/{owner}/{repo}/languages")
    Map<String, Integer> languages(@Param("owner") String owner, @Param("repo") String repo);
    
    @ErrorHandling
    @RequestLine("GET /repos/{owner}/{repo}/team")
    List<Team> languages(@Param("owner") String owner, @Param("repo") String repo);
}
```

After:
```java
@ErrorHandling(codeSpecific =
    {
        @ErrorCodes( codes = {404}, generate = UnknownItemException.class)
    },
    defaultException = ClassLevelDefaultException.class
)
interface GitHub {
    @NoDataErrorHandling
    @RequestLine("GET /repos/{owner}/{repo}/contributors")
    List<Contributor> contributors(@Param("owner") String owner, @Param("repo") String repo);

    @NoDataErrorHandling
    @RequestLine("GET /repos/{owner}/{repo}/languages")
    Map<String, Integer> languages(@Param("owner") String owner, @Param("repo") String repo);
    
    @ErrorHandling
    @RequestLine("GET /repos/{owner}/{repo}/team")
    List<Team> languages(@Param("owner") String owner, @Param("repo") String repo);
}
```
