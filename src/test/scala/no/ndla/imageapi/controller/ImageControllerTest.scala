/*
 * Part of NDLA image_api.
 * Copyright (C) 2016 NDLA
 *
 * See LICENSE
 *
 */

package no.ndla.imageapi.controller

import java.util.Date

import io.digitallibrary.language.model.LanguageTag
import no.ndla.imageapi.ImageApiProperties.MaxImageFileSizeBytes
import no.ndla.imageapi.model.api.NewImageMetaInformation
import no.ndla.imageapi.model.domain
import no.ndla.imageapi.{ImageSwagger, TestEnvironment, UnitSuite}
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatra.servlet.FileItem
import org.scalatra.test.Uploadable
import org.scalatra.test.scalatest.ScalatraSuite

import scala.reflect.api
import scala.util.Success

class ImageControllerTest extends UnitSuite with ScalatraSuite with TestEnvironment {

  // Use jwt.io to decode the jwt
  val jwtHeader = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9"

  val jwtClaims = "eyJodHRwczovL2RpZ2l0YWxsaWJyYXJ5LmlvL2dkbF9pZCI6ImFiYzEyMyIsImh0dHBzOi8vZGlnaXRhbGxpYnJhcnkuaW8vdXNlcl9uYW1lIjoiRG9uYWxkIER1Y2siLCJpc3MiOiJodHRwczovL3NvbWUtZG9tYWluLyIsInN1YiI6Imdvb2dsZS1vYXV0aDJ8MTIzIiwiYXVkIjoiYWJjIiwiZXhwIjoxNDg2MDcwMDYzLCJpYXQiOjE0ODYwMzQwNjMsInNjb3BlIjoiaW1hZ2VzLWxvY2FsOndyaXRlIn0"
  val jwtClaimsNoRoles = "eyJodHRwczovL2RpZ2l0YWxsaWJyYXJ5LmlvL2dkbF9pZCI6ImFiYzEyMyIsIm5hbWUiOiJEb25hbGQgRHVjayIsImlzcyI6Imh0dHBzOi8vc29tZS1kb21haW4vIiwic3ViIjoiZ29vZ2xlLW9hdXRoMnwxMjMiLCJhdWQiOiJhYmMiLCJleHAiOjE0ODYwNzAwNjMsImlhdCI6MTQ4NjAzNDA2M30"
  val jwtClaimsWrongRole = "eyJodHRwczovL2RpZ2l0YWxsaWJyYXJ5LmlvL2dkbF9pZCI6ImFiYzEyMyIsIm5hbWUiOiJEb25hbGQgRHVjayIsImlzcyI6Imh0dHBzOi8vc29tZS1kb21haW4vIiwic3ViIjoiZ29vZ2xlLW9hdXRoMnwxMjMiLCJhdWQiOiJhYmMiLCJleHAiOjE0ODYwNzAwNjMsImlhdCI6MTQ4NjAzNDA2Mywic2NvcGUiOiJpbWFnZTpyZWFkIn0"

  val authHeaderWithWriteRole = s"Bearer $jwtHeader.$jwtClaims.nAJRL8tPJSoX9wgsQ2znSAG7f8geW-gltofJm4YWdV4"
  val authHeaderWithoutAnyRoles = s"Bearer $jwtHeader.$jwtClaimsNoRoles.eNEK5datycKuV292kOxT4IhCMvrrq0KpSyH8C69mdnM"
  val authHeaderWithWrongRole = s"Bearer $jwtHeader.$jwtClaimsWrongRole.VxqM2bu2UF8IAalibIgdRdmsTDDWKEYpKzHPbCJcFzA"

  implicit val swagger = new ImageSwagger
  lazy val controller = new ImageController
  addServlet(controller, "/*")

  override def beforeEach(): Unit = {
    reset(searchService)
  }

  case class PretendFile(content: Array[Byte], contentType: String, fileName: String) extends Uploadable {
    override def contentLength: Long = content.length
  }

  val sampleUploadFile = PretendFile(Array[Byte](-1, -40, -1), "image/jpeg", "image.jpg")

  val sampleNewImageMeta =
    """
      |{
      |    "title": "Utedo med hjerte på døra",
      |    "alttext": "En skeiv utedodør med utskåret hjerte. Foto.",
      |    "copyright": {
      |        "origin": "http://www.scanpix.no",
      |        "authors": [],
      |        "license": {
      |            "description": "Creative Commons Attribution-ShareAlike 2.0 Generic",
      |            "url": "https://creativecommons.org/licenses/by-sa/2.0/",
      |            "license": "by-nc-sa"
      |        }
      |    }
      |    "caption": "",
      |    "tags": [],
      |    "language": "nb"
      |}
      |
    """.stripMargin

  test("That POST / returns 400 if parameters are missing") {
    post("/", Map("metadata" -> sampleNewImageMeta), headers = Map("Authorization" -> authHeaderWithWriteRole)) {
      status should equal (400)
    }
  }


  test("That POST / returns 200 if everything went well") {
    val titles: Seq[domain.ImageTitle] = Seq()
    val alttexts: Seq[domain.ImageAltText] = Seq()
    val copyright = domain.Copyright(domain.License("by", "description", None), "", Seq.empty)
    val tags: Seq[domain.ImageTag] = Seq()
    val captions: Seq[domain.ImageCaption] = Seq()

    val sampleImageMeta = domain.ImageMetaInformation(Some(1), titles, alttexts, "http://some.url/img.jpg", 1024, "image/jpeg", copyright, tags, captions, "updatedBy", new Date())
    when(writeService.storeNewImage(any[NewImageMetaInformation], any[FileItem])).thenReturn(Success(sampleImageMeta))

    post("/", Map("metadata" -> sampleNewImageMeta), Map("file" -> sampleUploadFile), headers = Map("Authorization" -> authHeaderWithWriteRole)) {
      status should equal (200)
    }
  }

  test("That POST / returns 400 if filename lacks proper extension") {
    def assert400(filename: String) = {
      post("/", Map("metadata" -> sampleNewImageMeta), Map("file" -> sampleUploadFile.copy(fileName = filename)), headers = Map("Authorization" -> authHeaderWithWriteRole)) {
        status should equal (400)
      }
    }
    assert400("filename")
    assert400("filename.")
    assert400("filename.j")
    assert400("filename.jp")
    assert400("filename.jpg.")
    assert400("filename.jpg.p")
    assert400("filename.jpg.pn")
  }

  test("That POST / returns 403 if no auth-header") {
    post("/", Map("metadata" -> sampleNewImageMeta)) {
      status should equal (403)
    }
  }

  test("That POST / returns 403 if auth header does not have expected role") {
    post("/", Map("metadata" -> sampleNewImageMeta), headers = Map("Authorization" -> authHeaderWithWrongRole)) {
      status should equal (403)
    }
  }

  test("That POST / returns 403 if auth header does not have any roles") {
    post("/", Map("metadata" -> sampleNewImageMeta), headers = Map("Authorization" -> authHeaderWithoutAnyRoles)) {
      status should equal (403)
    }
  }

  test("That POST / returns 413 if file is too big") {
    val content: Array[Byte] = Array.fill(MaxImageFileSizeBytes + 1) { 0 }
    post("/", Map("metadata" -> sampleNewImageMeta), Map("file" -> sampleUploadFile.copy(content)), headers = Map("Authorization" -> authHeaderWithWriteRole)) {
      status should equal (413)
    }
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
