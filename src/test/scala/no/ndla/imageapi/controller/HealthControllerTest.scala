/*
 * Part of NDLA image_api.
 * Copyright (C) 2016 NDLA
 *
 * See LICENSE
 *
 */

package no.ndla.imageapi.controller

import no.ndla.imageapi.{TestEnvironment, UnitSuite}
import org.mockito.Mockito.{reset, verify, verifyNoMoreInteractions, when}
import org.scalatra.test.scalatest.ScalatraFunSuite

class HealthControllerTest extends UnitSuite with TestEnvironment with ScalatraFunSuite {

  val checkThatEndpointResponds: CheckThatEndpointResponds = mock[CheckThatEndpointResponds]
  val searchUrlV1 = "http://0.0.0.0:80/image-api/v1/images/"
  val searchUrlV2 = "http://0.0.0.0:80/image-api/v2/images/"

  override def beforeEach(): Unit = reset(checkThatEndpointResponds)

  lazy val controller = new HealthController {
    override def checker = checkThatEndpointResponds
  }
  addServlet(controller, "/")

  test("that /health returns 200 as long as any response is received for all endpoints") {
    when(checkThatEndpointResponds.responds(searchUrlV1)).thenReturn(true)
    when(checkThatEndpointResponds.responds(searchUrlV2)).thenReturn(true)
    get("/") {
      status should equal(200)
    }
    verify(checkThatEndpointResponds).responds(searchUrlV1)
    verify(checkThatEndpointResponds).responds(searchUrlV2)
    verifyNoMoreInteractions(checkThatEndpointResponds)
  }

  test("that /health returns 500 if no response is received for at least one endpoint") {
    when(checkThatEndpointResponds.responds(searchUrlV1)).thenReturn(true)
    when(checkThatEndpointResponds.responds(searchUrlV2)).thenReturn(false)
    get("/") {
      status should equal(500)
    }
    verify(checkThatEndpointResponds).responds(searchUrlV1)
    verify(checkThatEndpointResponds).responds(searchUrlV2)
    verifyNoMoreInteractions(checkThatEndpointResponds)
  }

  test("that /health returns 500 if no response is received for at least one endpoint, and is short circuited") {
    when(checkThatEndpointResponds.responds(searchUrlV1)).thenReturn(false)
    get("/") {
      status should equal(500)
    }
    verify(checkThatEndpointResponds).responds(searchUrlV1)
    verifyNoMoreInteractions(checkThatEndpointResponds)
  }

}
