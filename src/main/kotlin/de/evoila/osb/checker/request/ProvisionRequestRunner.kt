package de.evoila.osb.checker.request

import de.evoila.osb.checker.config.Configuration
import de.evoila.osb.checker.config.Configuration.token
import de.evoila.osb.checker.request.bodies.RequestBody
import de.evoila.osb.checker.response.LastOperationResponse
import io.restassured.RestAssured
import io.restassured.http.ContentType
import io.restassured.http.Header

object ProvisionRequestRunner {

  fun runPutProvisionRequest(requestBody: RequestBody, expectedStatusCode: Int) {
    RestAssured.with()
        .header(Header("X-Broker-API-Version", Configuration.apiVersion))
        .header(Header("Authorization", token))
        .contentType(ContentType.JSON)
        .body(requestBody)
        .put("/v2/service_instances/${Configuration.INSTANCE_ID}?accepts_incomplete=true")
        .then()
        .assertThat()
        .statusCode(expectedStatusCode)
  }

  fun runPatchProvisionRequest(requestBody: RequestBody, expectedStatusCode: Int) {
    RestAssured.with()
        .header(Header("X-Broker-API-Version", Configuration.apiVersion))
        .header(Header("Authorization", token))
        .contentType(ContentType.JSON)
        .body(requestBody)
        .put("/v2/service_instances/${Configuration.INSTANCE_ID}?accepts_incomplete=true")
        .then()
        .assertThat()
        .statusCode(expectedStatusCode)
  }

  fun validLastOperationStatus(requestBody: RequestBody) {
    runGetLastOperation(requestBody, 200)
  }

  fun runGetLastOperation(requestBody: RequestBody, expectedStatusCode: Int) {
    RestAssured.with()
        .header(Header("X-Broker-API-Version", Configuration.apiVersion))
        .header(Header("Authorization", token))
        .contentType(ContentType.JSON)
        .get("/v2/service_instances/${Configuration.INSTANCE_ID}/last_operation")
        .then()
        .assertThat()
        .statusCode(expectedStatusCode)
  }

  fun waitForFinish(): String {
    val response = RestAssured.with()
        .header(Header("X-Broker-API-Version", Configuration.apiVersion))
        .header(Header("Authorization", token))
        .contentType(ContentType.JSON)
        .get("/v2/service_instances/${Configuration.INSTANCE_ID}/last_operation")
        .then()
        .assertThat()
        .extract()
        .response()
        .jsonPath()
        .getObject("", LastOperationResponse::class.java)

    if (response.state == "in progress") {
      Thread.sleep(10000)
      return waitForFinish()
    }
    return response.state
  }

  fun runDeleteProvisionRequest(serviceId: String?, planId: String?, expectedStatusCode: Int) {

    var path = "/v2/service_instances/${Configuration.INSTANCE_ID}?accepts_incomplete=true"

    path = serviceId?.let { "$path&service_id=$serviceId" } ?: path
    path = planId?.let { "$path&plan_id=$planId" } ?: path

    val statusCode = RestAssured.with()
        .header(Header("X-Broker-API-Version", Configuration.apiVersion))
        .header(Header("Authorization", token))
        .contentType(ContentType.JSON)
        .delete(path)
        .then()
        .extract()
        .statusCode()

    assert(statusCode == 200 || statusCode == 202)
  }
}