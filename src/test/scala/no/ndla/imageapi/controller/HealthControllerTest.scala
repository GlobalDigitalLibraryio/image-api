/*
 * Part of NDLA image_api.
 * Copyright (C) 2016 NDLA
 *
 * See LICENSE
 *
 */

package no.ndla.imageapi.controller

import no.ndla.imageapi.{TestData, TestEnvironment, UnitSuite}
import org.mockito.Mockito.when
import org.scalatra.test.scalatest.ScalatraFunSuite
import scalaj.http.HttpResponse

class HealthControllerTest extends UnitSuite with TestEnvironment with ScalatraFunSuite {


  lazy val controller = new HealthController

  addServlet(controller, "/")

  test("that /health returns 200 on success") {
    when(imageRepository.getRandomImage()).thenReturn(Some(TestData.bjorn))

    get("/") {
      status should equal(200)
    }
  }

  test("that /health returns 200 on no images") {
    when(imageRepository.getRandomImage()).thenReturn(None)

    get("/") {
      status should equal(200)
    }
  }

  test("that /health returns 500 on failure") {
    when(imageRepository.getRandomImage()).thenThrow(new RuntimeException("Could not query database"))

    get("/") {
      status should equal(500)
    }
  }


}
