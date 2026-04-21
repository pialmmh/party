package com.telcobright.party.api.resource;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
class PingResourceTest {

    @Test
    void pingReturnsOk() {
        given()
                .when().get("/ping")
                .then()
                .statusCode(200)
                .body("service", equalTo("party"))
                .body("status", equalTo("ok"));
    }

}
