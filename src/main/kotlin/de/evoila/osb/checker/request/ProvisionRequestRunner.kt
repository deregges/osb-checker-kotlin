package de.evoila.osb.checker.request

import de.evoila.osb.checker.config.Configuration
import de.evoila.osb.checker.request.ResponseBodyType.*
import de.evoila.osb.checker.request.bodies.RequestBody
import de.evoila.osb.checker.response.catalog.ServiceInstance
import de.evoila.osb.checker.response.operations.LastOperationResponse.State
import io.restassured.RestAssured
import io.restassured.http.ContentType
import io.restassured.http.Header
import io.restassured.module.jsv.JsonSchemaValidator
import io.restassured.response.ExtractableResponse
import io.restassured.response.Response
import org.hamcrest.collection.IsIn
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class ProvisionRequestRunner(
        configuration: Configuration
) : PollingRequestHandler(
        configuration
) {

    fun getProvision(instanceId: String, retrievable: Boolean): ServiceInstance {
        return RestAssured.with()
                .log().ifValidationFails()
                .headers(validRequestHeaders)
                .contentType(ContentType.JSON)
                .get(SERVICE_INSTANCE_PATH + instanceId)
                .then()
                .log().ifValidationFails()
                .assertThat()
                .statusCode(200)
                .headers(expectedResponseHeaders)
                .body(JsonSchemaValidator.matchesJsonSchemaInClasspath(VALID_FETCH_INSTANCE.path))
                .extract()
                .response()
                .jsonPath()
                .getObject("", ServiceInstance::class.java)
    }

    fun runPutProvisionRequestSync(instanceId: String,
                                   requestBody: RequestBody) {
        val response = RestAssured.with()
                .log().ifValidationFails()
                .headers(validRequestHeaders)
                .contentType(ContentType.JSON)
                .body(requestBody)
                .put(SERVICE_INSTANCE_PATH + instanceId)
                .then()
                .assertThat()
                .log().ifValidationFails()
                .statusCode(IsIn(listOf(201, 422)))
                .extract()

        if (response.statusCode() == 201) {
            val responseBodyString = response.jsonPath().prettify()
            assert(JsonSchemaValidator.matchesJsonSchemaInClasspath(VALID_PROVISION.path)
                    .matches(responseBodyString)) { "Expected a valid provision response but was:\n$responseBodyString" }
        } else {
            val responseBodyString = response.jsonPath().prettify()
            assert(JsonSchemaValidator.matchesJsonSchemaInClasspath(ERR_ASYNC_REQUIRED.path)
                    .matches(responseBodyString)) { "Expected OSB error code async required but was:\n$responseBodyString" }
        }
    }

    fun runPutProvisionRequestAsync(instanceId: String,
                                    requestBody: RequestBody,
                                    vararg expectedFinalStatusCodes: Int,
                                    expectedResponseBodyType: ResponseBodyType)
            : ExtractableResponse<Response> {

        return RestAssured.with()
                .log().ifValidationFails()
                .headers(validRequestHeaders)
                .contentType(ContentType.JSON)
                .body(requestBody)
                .put(SERVICE_INSTANCE_PATH + instanceId + ACCEPTS_INCOMPLETE)
                .then()
                .log().ifValidationFails()
                .statusCode(IsIn(expectedFinalStatusCodes.asList()))
                .body(JsonSchemaValidator.matchesJsonSchemaInClasspath(expectedResponseBodyType.path))
                .assertThat()
                .extract()
    }

    fun polling(instanceId: String, expectedFinalStatusCode: Int, operationData: String?, maxPollingDuration: Int): State {
        val latestAcceptablePollingInstant = Instant.now().plusSeconds(maxPollingDuration.toLong())
        return super.waitForFinish(path = SERVICE_INSTANCE_PATH + instanceId + LAST_OPERATION,
                expectedFinalStatusCode = expectedFinalStatusCode,
                operationData = operationData,
                latestAcceptablePollingInstant = latestAcceptablePollingInstant
        )
    }

    fun runDeleteProvisionRequestSync(instanceId: String, serviceId: String?, planId: String?) {
        var path = SERVICE_INSTANCE_PATH + instanceId
        path = serviceId?.let { "$path?service_id=$serviceId" } ?: path
        path = planId?.let { "$path&plan_id=$planId" } ?: path
        val response = RestAssured.with()
                .log().ifValidationFails()
                .headers(validRequestHeaders)
                .contentType(ContentType.JSON)
                .delete(path)
                .then()
                .log().ifValidationFails()
                .headers(expectedResponseHeaders)
                .statusCode(IsIn(listOf(200, 422)))
                .extract()

        if (response.statusCode() != 200) {
            val responseBodyString = response.jsonPath().prettify()
            assert(JsonSchemaValidator.matchesJsonSchemaInClasspath(ERR_ASYNC_REQUIRED.path)
                    .matches(responseBodyString)) { "Expected OSB error code async required but was:\n$responseBodyString" }
        }
    }

    fun runDeleteProvisionRequestAsync(instanceId: String,
                                       serviceId: String?,
                                       planId: String?,
                                       expectedFinalStatusCodes: IntArray): ExtractableResponse<Response> {
        var path = SERVICE_INSTANCE_PATH + instanceId + ACCEPTS_INCOMPLETE
        path = serviceId?.let { "$path&service_id=$serviceId" } ?: path
        path = planId?.let { "$path&plan_id=$planId" } ?: path

        return RestAssured.with()
                .log().ifValidationFails()
                .headers(validRequestHeaders)
                .contentType(ContentType.JSON)
                .delete(path)
                .then()
                .log().ifValidationFails()
                .assertThat()
                .headers(expectedResponseHeaders)
                .statusCode(IsIn(expectedFinalStatusCodes.asList()))
                .extract()
    }

    fun putWithoutHeader() {
        RestAssured.with()
                .log().ifValidationFails()
                .header(Header("Authorization", configuration.correctToken))
                .put(SERVICE_INSTANCE_PATH + Configuration.notAnId + ACCEPTS_INCOMPLETE)
                .then()
                .log().ifValidationFails()
                .assertThat()
                .statusCode(412)
    }

    fun deleteWithoutHeader() {
        RestAssured.with()
                .log().ifValidationFails()
                .header(Header("Authorization", configuration.correctToken))
                .delete("$SERVICE_INSTANCE_PATH${Configuration.notAnId}$ACCEPTS_INCOMPLETE&service_id=Invalid&plan_id=Invalid")
                .then()
                .log().ifValidationFails()
                .assertThat()
                .statusCode(412)
    }

    fun lastOperationWithoutHeader() {
        RestAssured.with()
                .log().ifValidationFails()
                .header(Header("Authorization", configuration.correctToken))
                .get(SERVICE_INSTANCE_PATH + Configuration.notAnId + LAST_OPERATION)
                .then()
                .log().ifValidationFails()
                .assertThat()
                .statusCode(412)
    }

    fun putNoAuth() {
        RestAssured.with()
                .log().ifValidationFails()
                .header(Header("X-Broker-API-Version", "${configuration.apiVersion}"))
                .contentType(ContentType.JSON)
                .put(SERVICE_INSTANCE_PATH + Configuration.notAnId + ACCEPTS_INCOMPLETE)
                .then()
                .log().ifValidationFails()
                .assertThat()
                .statusCode(401)
    }

    fun putWrongUser() {
        RestAssured.with()
                .log().ifValidationFails()
                .header(Header("Authorization", configuration.wrongUserToken))
                .header(Header("X-Broker-API-Version", "${configuration.apiVersion}"))
                .contentType(ContentType.JSON)
                .put(SERVICE_INSTANCE_PATH + Configuration.notAnId + ACCEPTS_INCOMPLETE)
                .then()
                .log().ifValidationFails()
                .assertThat()
                .statusCode(401)
    }

    fun putWrongPassword() {
        RestAssured.with()
                .log().ifValidationFails()
                .header(Header("Authorization", configuration.wrongPasswordToken))
                .header(Header("X-Broker-API-Version", "${configuration.apiVersion}"))
                .contentType(ContentType.JSON)
                .put(SERVICE_INSTANCE_PATH + Configuration.notAnId + ACCEPTS_INCOMPLETE)
                .then()
                .log().ifValidationFails()
                .assertThat()
                .statusCode(401)
    }

    fun deleteNoAuth() {
        RestAssured.with()
                .log().ifValidationFails()
                .header(Header("X-Broker-API-Version", "${configuration.apiVersion}"))
                .contentType(ContentType.JSON)
                .delete(SERVICE_INSTANCE_PATH + Configuration.notAnId + ACCEPTS_INCOMPLETE)
                .then()
                .log().ifValidationFails()
                .assertThat()
                .statusCode(401)
    }

    fun deleteWrongUser() {
        RestAssured.with()
                .log().ifValidationFails()
                .header(Header("Authorization", configuration.wrongUserToken))
                .header(Header("X-Broker-API-Version", "${configuration.apiVersion}"))
                .contentType(ContentType.JSON)
                .delete(SERVICE_INSTANCE_PATH + Configuration.notAnId + ACCEPTS_INCOMPLETE)
                .then()
                .log().ifValidationFails()
                .assertThat()
                .statusCode(401)
    }

    fun deleteWrongPassword() {
        RestAssured.with()
                .log().ifValidationFails()
                .header(Header("Authorization", configuration.wrongPasswordToken))
                .header(Header("X-Broker-API-Version", "${configuration.apiVersion}"))
                .contentType(ContentType.JSON)
                .delete(SERVICE_INSTANCE_PATH + Configuration.notAnId + ACCEPTS_INCOMPLETE)
                .then()
                .log().ifValidationFails()
                .assertThat()
                .statusCode(401)
    }

    fun lastOpNoAuth() {
        RestAssured.with()
                .log().ifValidationFails()
                .header(Header("X-Broker-API-Version", "${configuration.apiVersion}"))
                .contentType(ContentType.JSON)
                .get(SERVICE_INSTANCE_PATH + Configuration.notAnId + LAST_OPERATION)
                .then()
                .log().ifValidationFails()
                .assertThat()
                .statusCode(401)
    }

    fun lastOpWrongUser() {
        RestAssured.with()
                .log().ifValidationFails()
                .header(Header("Authorization", configuration.wrongUserToken))
                .header(Header("X-Broker-API-Version", "${configuration.apiVersion}"))
                .contentType(ContentType.JSON)
                .get(SERVICE_INSTANCE_PATH + Configuration.notAnId + LAST_OPERATION)
                .then()
                .log().ifValidationFails()
                .assertThat()
                .statusCode(401)
    }

    fun lastOpWrongPassword() {
        RestAssured.with()
                .log().ifValidationFails()
                .header(Header("Authorization", configuration.wrongPasswordToken))
                .header(Header("X-Broker-API-Version", "${configuration.apiVersion}"))
                .contentType(ContentType.JSON)
                .get(SERVICE_INSTANCE_PATH + Configuration.notAnId + LAST_OPERATION)
                .then()
                .log().ifValidationFails()
                .assertThat()
                .statusCode(401)
    }
}