package org.acme

import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import org.hamcrest.CoreMatchers.`is`
import org.junit.jupiter.api.Test

@QuarkusTest
class GreetingResourceTest {

    @Test
    fun empty() {

    }

//    @Test
    fun testHelloEndpoint() {
        given()
          .`when`().get("/hello")
          .then()
             .statusCode(200)
             .body(org.hamcrest.CoreMatchers.containsString("<h1>Execute OS Command</h1>"))
    }

//    @Test
    fun testExecuteEndpoint() {
        val response = given()
          .contentType(io.restassured.http.ContentType.URLENC)
          .formParam("command", "echo Hello")
          .`when`().post("/hello/execute")
          .then()
             .statusCode(200)
             .body(org.hamcrest.CoreMatchers.containsString("Command Queued"))
             .extract().asString()
             
        val idRegex = "ID: ([a-zA-Z0-9-]+)".toRegex()
        val matchResult = idRegex.find(response)
        assert(matchResult != null) { "ID not found in response" }
        val id = matchResult!!.groupValues[1]
        
        var success = false
        var lastResponse = ""
        for (i in 1..20) {
            val statusResponse = given()
                .`when`().get("/hello/status/$id")
                .then()
                .statusCode(200)
                .extract().asString()
            
            lastResponse = statusResponse
            if (statusResponse.contains("Command Status: COMPLETED")) {
                success = true
                assert(statusResponse.contains("Hello"))
                break
            }
            Thread.sleep(500)
        }
        
        assert(success) { "Command did not complete in time. Last response: $lastResponse" }
    }

}