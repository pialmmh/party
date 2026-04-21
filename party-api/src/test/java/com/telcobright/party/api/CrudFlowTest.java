package com.telcobright.party.api;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * End-to-end smoke: create operator, tenant, partner; verify sync jobs were enqueued.
 * Runs against Quarkus Dev Services MariaDB with the full master schema applied by Flyway.
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CrudFlowTest {

    static long operatorId;
    static long tenantId;
    static long partnerId;

    @Test @Order(1)
    void createOperator() {
        operatorId = given()
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "shortName", "btcl",
                        "fullName", "Bangladesh Telecommunications Company Ltd",
                        "operatorType", "MNO",
                        "companyName", "BTCL",
                        "country", "BD",
                        "status", "ACTIVE"))
                .when().post("/operators")
                .then()
                .statusCode(200)
                .body("shortName", equalTo("btcl"))
                .body("id", notNullValue())
                .extract().jsonPath().getLong("id");
    }

    @Test @Order(2)
    void createTenant() {
        tenantId = given()
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "shortName", "btcl",
                        "fullName", "BTCL primary tenant",
                        "dbHost", "127.0.0.1",
                        "dbPort", 3306,
                        "dbUser", "party",
                        "dbPassRef", "PARTY_TENANT_DB_PASS"))
                .when().post("/operators/" + operatorId + "/tenants")
                .then()
                .statusCode(200)
                .body("shortName", equalTo("btcl"))
                .extract().jsonPath().getLong("id");
    }

    @Test @Order(3)
    void createPartner() {
        partnerId = given()
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "partnerName", "Acme Reseller",
                        "partnerType", "RESELLER",
                        "email", "acme@example.com",
                        "customerPrepaid", true,
                        "defaultCurrency", 1))
                .when().post("/tenants/" + tenantId + "/partners")
                .then()
                .statusCode(200)
                .body("partnerName", equalTo("Acme Reseller"))
                .body("tenantId", equalTo((int) tenantId))
                .extract().jsonPath().getLong("id");
    }

    @Test @Order(4)
    void listPartners() {
        given()
                .when().get("/tenants/" + tenantId + "/partners")
                .then()
                .statusCode(200)
                .body("size()", greaterThanOrEqualTo(1))
                .body("[0].partnerName", equalTo("Acme Reseller"));
    }

    @Test @Order(5)
    void syncJobsWereEnqueued() {
        given()
                .when().get("/tenants/" + tenantId + "/sync-jobs")
                .then()
                .statusCode(200)
                .body("size()", greaterThanOrEqualTo(2))   // provision + partner-create
                .body("status", hasItem("PENDING"));
    }

    @Test @Order(6)
    void createOperatorUserAndLogin() {
        // create a fresh operator user so we know the password
        given()
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "email", "test-admin@example.com",
                        "password", "SecurePass123!",
                        "firstName", "Test",
                        "lastName", "Admin",
                        "role", "OPERATOR_ADMIN",
                        "operatorId", operatorId))
                .when().post("/operator-users")
                .then()
                .statusCode(200)
                .body("email", equalTo("test-admin@example.com"));

        given()
                .contentType(ContentType.JSON)
                .body(Map.of("email", "test-admin@example.com", "password", "SecurePass123!"))
                .when().post("/auth/login")
                .then()
                .statusCode(200)
                .body("scope", equalTo("OPERATOR_ADMIN"))
                .body("accessToken", notNullValue())
                .body("operatorId", equalTo((int) operatorId));
    }

    @Test @Order(7)
    void loginWithBadCredsReturns401() {
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("email", "nobody@example.com", "password", "wrong"))
                .when().post("/auth/login")
                .then()
                .statusCode(401);
    }
}
