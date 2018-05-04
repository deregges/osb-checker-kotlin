package de.evoila.osb.checker.tests

import com.greghaskins.spectrum.Spectrum
import com.greghaskins.spectrum.Spectrum.it
import de.evoila.osb.checker.config.Configuration
import de.evoila.osb.checker.request.CatalogRequestRunner
import de.evoila.osb.checker.response.Catalog
import io.restassured.RestAssured
import io.restassured.RestAssured.*
import io.restassured.http.ContentType
import io.restassured.http.Header
import org.junit.runner.RunWith


@RunWith(Spectrum::class)
class CatalogTests {

  init {
    baseURI = Configuration.url
    RestAssured.port = port
    authentication = basic("admin", "cloudfoundry")


    Spectrum.describe("Testing the catalog") {

      it("should reject requests without X-Broker-API-Version header with 412") {
        CatalogRequestRunner.withoutHeader(Configuration.token)
      }

      it("should return list of registered service classes as JSON payload") {
        CatalogRequestRunner.correctRequest(Configuration.token)
      }
    }
  }
}