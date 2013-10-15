package feign.jackson.examples;

import feign.Feign;
import feign.RequestLine;
import feign.jackson.JacksonDecoder;

import javax.inject.Named;
import java.util.List;

/**
 * adapted from {@code com.example.retrofit.GitHubClient}
 */
public class GitHubExample {
  interface GitHub {
    @RequestLine("GET /repos/{owner}/{repo}/contributors")
    List<Contributor> contributors(@Named("owner") String owner, @Named("repo") String repo);
  }

  static class Contributor {
    private String login;
    private int contributions;

    void setLogin(String login) {
        this.login = login;
    }

    void setContributions(int contributions) {
        this.contributions = contributions;
    }
  }

  public static void main(String... args) throws InterruptedException {
    GitHub github = Feign.builder().decoder(new JacksonDecoder()).target(GitHub.class, "https://api.github.com");
    System.out.println("Let's fetch and print a list of the contributors to this library.");
    List<Contributor> contributors = github.contributors("netflix", "feign");
    for (Contributor contributor : contributors) {
      System.out.println(contributor.login + " (" + contributor.contributions + ")");
    }
  }
}
