package com.ecommerce.order.loadtest

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._

/**
 * Gatling load test for Order Service.
 * Tests the order confirmation endpoint under load.
 *
 * Target: 100 orders/sec sustained load
 *
 * Run with: ./gradlew :order-service:gatlingRun
 */
class OrderLoadTest extends Simulation {

  // HTTP configuration
  val httpProtocol = http
    .baseUrl("http://localhost:8080")
    .acceptHeader("application/json")
    .contentTypeHeader("application/json")
    .userAgentHeader("Gatling Load Test")

  // Generate unique order data
  val orderFeeder = Iterator.continually(Map(
    "orderId" -> java.util.UUID.randomUUID().toString,
    "userId" -> s"user-${scala.util.Random.nextInt(1000)}",
    "amount" -> (10 + scala.util.Random.nextDouble() * 990).formatted("%.2f"),
    "productId" -> s"SKU-${1000 + scala.util.Random.nextInt(100)}"
  ))

  // Order confirmation scenario
  val confirmOrderScenario = scenario("Order Confirmation Load Test")
    .feed(orderFeeder)
    .exec(
      http("Confirm Order")
        .post("/api/v1/orders/confirm")
        .body(StringBody(
          """{
            |  "orderId": "${orderId}",
            |  "userId": "${userId}",
            |  "items": [
            |    {
            |      "productId": "${productId}",
            |      "productName": "Test Product",
            |      "quantity": 1,
            |      "unitPrice": ${amount}
            |    }
            |  ],
            |  "totalAmount": ${amount},
            |  "creditCardNumber": "4111111111111111",
            |  "shippingAddress": "123 Test Street, Test City, TC 12345"
            |}""".stripMargin))
        .check(status.in(200, 202))
        .check(jsonPath("$.txId").exists.saveAs("txId"))
    )
    .pause(100.milliseconds, 500.milliseconds)
    .exec(
      http("Check Transaction Status")
        .get("/api/v1/transactions/${txId}")
        .check(status.is(200))
    )

  // Transaction query scenario (read-heavy)
  val queryTransactionScenario = scenario("Transaction Query Load Test")
    .feed(orderFeeder)
    .exec(
      http("Confirm Order for Query")
        .post("/api/v1/orders/confirm")
        .body(StringBody(
          """{
            |  "orderId": "${orderId}",
            |  "userId": "${userId}",
            |  "items": [
            |    {
            |      "productId": "${productId}",
            |      "productName": "Test Product",
            |      "quantity": 1,
            |      "unitPrice": ${amount}
            |    }
            |  ],
            |  "totalAmount": ${amount},
            |  "creditCardNumber": "4111111111111111",
            |  "shippingAddress": "123 Test Street, Test City, TC 12345"
            |}""".stripMargin))
        .check(status.in(200, 202))
        .check(jsonPath("$.txId").exists.saveAs("txId"))
    )
    .pause(500.milliseconds)
    .repeat(5) {
      exec(
        http("Query Transaction")
          .get("/api/v1/transactions/${txId}")
          .check(status.is(200))
      )
      .pause(200.milliseconds, 500.milliseconds)
    }

  // Load test setup
  setUp(
    // Scenario 1: Ramp up to 100 orders/sec over 30 seconds, sustain for 2 minutes
    confirmOrderScenario.inject(
      rampUsersPerSec(1).to(100).during(30.seconds),
      constantUsersPerSec(100).during(2.minutes)
    ),

    // Scenario 2: Additional read load (50 users querying)
    queryTransactionScenario.inject(
      rampUsers(50).during(30.seconds)
    )
  ).protocols(httpProtocol)
   .assertions(
     // Global assertions
     global.responseTime.max.lt(5000),           // Max response time < 5 seconds
     global.responseTime.percentile(95).lt(2000), // 95th percentile < 2 seconds
     global.successfulRequests.percent.gt(95),   // > 95% success rate

     // Per-request assertions
     details("Confirm Order").responseTime.percentile(99).lt(3000),
     details("Check Transaction Status").responseTime.percentile(99).lt(1000)
   )
}

/**
 * Smoke test - quick validation that endpoints work
 */
class OrderSmokeTest extends Simulation {

  val httpProtocol = http
    .baseUrl("http://localhost:8080")
    .acceptHeader("application/json")
    .contentTypeHeader("application/json")

  val smokeTestScenario = scenario("Smoke Test")
    .exec(
      http("Health Check")
        .get("/actuator/health")
        .check(status.is(200))
    )
    .pause(1.second)
    .exec(
      http("Confirm Single Order")
        .post("/api/v1/orders/confirm")
        .body(StringBody(
          """{
            |  "orderId": "smoke-test-order",
            |  "userId": "smoke-test-user",
            |  "items": [
            |    {
            |      "productId": "SKU-SMOKE",
            |      "productName": "Smoke Test Product",
            |      "quantity": 1,
            |      "unitPrice": 99.99
            |    }
            |  ],
            |  "totalAmount": 99.99,
            |  "creditCardNumber": "4111111111111111",
            |  "shippingAddress": "Smoke Test Address"
            |}""".stripMargin))
        .check(status.in(200, 202))
        .check(jsonPath("$.txId").exists)
    )

  setUp(
    smokeTestScenario.inject(atOnceUsers(1))
  ).protocols(httpProtocol)
}

/**
 * Stress test - find breaking point
 */
class OrderStressTest extends Simulation {

  val httpProtocol = http
    .baseUrl("http://localhost:8080")
    .acceptHeader("application/json")
    .contentTypeHeader("application/json")

  val orderFeeder = Iterator.continually(Map(
    "orderId" -> java.util.UUID.randomUUID().toString,
    "userId" -> s"user-${scala.util.Random.nextInt(1000)}",
    "amount" -> (10 + scala.util.Random.nextDouble() * 990).formatted("%.2f"),
    "productId" -> s"SKU-${1000 + scala.util.Random.nextInt(100)}"
  ))

  val stressScenario = scenario("Stress Test")
    .feed(orderFeeder)
    .exec(
      http("Confirm Order Under Stress")
        .post("/api/v1/orders/confirm")
        .body(StringBody(
          """{
            |  "orderId": "${orderId}",
            |  "userId": "${userId}",
            |  "items": [
            |    {
            |      "productId": "${productId}",
            |      "productName": "Stress Test Product",
            |      "quantity": 1,
            |      "unitPrice": ${amount}
            |    }
            |  ],
            |  "totalAmount": ${amount},
            |  "creditCardNumber": "4111111111111111",
            |  "shippingAddress": "Stress Test Address"
            |}""".stripMargin))
        .check(status.in(200, 202, 429, 503)) // Allow rate limiting responses
    )

  setUp(
    stressScenario.inject(
      // Gradually increase load to find breaking point
      incrementUsersPerSec(10)
        .times(20)
        .eachLevelLasting(10.seconds)
        .separatedByRampsLasting(5.seconds)
        .startingFrom(10)
    )
  ).protocols(httpProtocol)
   .assertions(
     global.failedRequests.percent.lt(50) // Allow up to 50% failures in stress test
   )
}
