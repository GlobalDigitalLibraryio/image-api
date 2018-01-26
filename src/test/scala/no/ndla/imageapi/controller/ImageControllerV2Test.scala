/*
 * Part of NDLA image_api.
 * Copyright (C) 2016 NDLA
 *
 * See LICENSE
 *
 */

package no.ndla.imageapi.controller

import io.digitallibrary.language.model.LanguageTag
import no.ndla.imageapi.{ImageSwagger, TestEnvironment, UnitSuite}
import org.mockito.Mockito._
import org.scalatra.test.scalatest.ScalatraSuite

class ImageControllerV2Test extends UnitSuite with ScalatraSuite with TestEnvironment {

  // Use jwt.io to decode the jwt
  val jwtHeader = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9"

  val jwtClaims = "eyJodHRwczovL2RpZ2l0YWxsaWJyYXJ5LmlvL2dkbF9pZCI6ImFiYzEyMyIsImh0dHBzOi8vZGlnaXRhbGxpYnJhcnkuaW8vdXNlcl9uYW1lIjoiRG9uYWxkIER1Y2siLCJpc3MiOiJodHRwczovL3NvbWUtZG9tYWluLyIsInN1YiI6Imdvb2dsZS1vYXV0aDJ8MTIzIiwiYXVkIjoiYWJjIiwiZXhwIjoxNDg2MDcwMDYzLCJpYXQiOjE0ODYwMzQwNjMsInNjb3BlIjoiaW1hZ2VzLWxvY2FsOndyaXRlIn0"
  val jwtClaimsNoRoles = "eyJodHRwczovL2RpZ2l0YWxsaWJyYXJ5LmlvL2dkbF9pZCI6ImFiYzEyMyIsIm5hbWUiOiJEb25hbGQgRHVjayIsImlzcyI6Imh0dHBzOi8vc29tZS1kb21haW4vIiwic3ViIjoiZ29vZ2xlLW9hdXRoMnwxMjMiLCJhdWQiOiJhYmMiLCJleHAiOjE0ODYwNzAwNjMsImlhdCI6MTQ4NjAzNDA2M30"
  val jwtClaimsWrongRole = "eyJodHRwczovL2RpZ2l0YWxsaWJyYXJ5LmlvL2dkbF9pZCI6ImFiYzEyMyIsIm5hbWUiOiJEb25hbGQgRHVjayIsImlzcyI6Imh0dHBzOi8vc29tZS1kb21haW4vIiwic3ViIjoiZ29vZ2xlLW9hdXRoMnwxMjMiLCJhdWQiOiJhYmMiLCJleHAiOjE0ODYwNzAwNjMsImlhdCI6MTQ4NjAzNDA2Mywic2NvcGUiOiJpbWFnZTpyZWFkIn0"

  val authHeaderWithWriteRole = s"Bearer $jwtHeader.$jwtClaims.nAJRL8tPJSoX9wgsQ2znSAG7f8geW-gltofJm4YWdV4"
  val authHeaderWithoutAnyRoles = s"Bearer $jwtHeader.$jwtClaimsNoRoles.eNEK5datycKuV292kOxT4IhCMvrrq0KpSyH8C69mdnM"
  val authHeaderWithWrongRole = s"Bearer $jwtHeader.$jwtClaimsWrongRole.VxqM2bu2UF8IAalibIgdRdmsTDDWKEYpKzHPbCJcFzA"

  implicit val swagger = new ImageSwagger
  lazy val controller = new ImageControllerV2
  addServlet(controller, "/*")

  override def beforeEach(): Unit = {
    reset(searchService)
  }

  test("that GET /?language=eng parses language ok") {
    get("/?language=eng") {
      status should equal (200)
    }
    verify(searchService).all(None, None, Some(LanguageTag("eng")), None, None)
  }

  test("that GET /?language=x doesn't fail when language can't be parsed, but uses language=None") {
    get("/?language=x") {
      status should equal (200)
    }
    verify(searchService).all(None, None, None, None, None)
  }

  test("that POST /search parses language ok") {
    post("/search/", """{"language": "eng"}""".getBytes) {
      status should equal (200)
    }
    verify(searchService).all(None, None, Some(LanguageTag("eng")), None, None)
  }

  test("that POST /search doesn't fail when language can't be parsed, but uses language=None") {
    post("/search/", """{"language": "x"}""".getBytes) {
      status should equal (200)
    }
    verify(searchService).all(None, None, None, None, None)
  }

}
