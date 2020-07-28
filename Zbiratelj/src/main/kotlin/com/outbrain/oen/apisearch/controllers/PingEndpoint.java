package com.outbrain.oen.apisearch.controllers;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PingEndpoint {

  @GetMapping("/ping")
  public String ping() {
    return "OK";
  }

  @GetMapping(value = "/", produces = "text/html")
  public String landingPage() {
    return "<html><body>" +
        "<h1>APISearch</h1><br/>" +
        "<a href=\"swagger-ui.html\">swagger</a><br/>" +
        "<a href=\"internal/metrics\">metrics</a><br/>" +
        "<a href=\"env\">env</a><br/>" +
        "<a href=\"selftest\">health</a><br/>" +
        "</body></html>";
  }
}
