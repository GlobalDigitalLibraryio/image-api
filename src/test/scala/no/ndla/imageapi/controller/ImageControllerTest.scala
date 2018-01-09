/*
 * Part of NDLA image_api.
 * Copyright (C) 2016 NDLA
 *
 * See LICENSE
 *
 */

package no.ndla.imageapi.controller

import java.util.Date

import no.ndla.imageapi.ImageApiProperties.MaxImageFileSizeBytes
import no.ndla.imageapi.model.api.NewImageMetaInformation
import no.ndla.imageapi.model.domain._
import no.ndla.imageapi.{ImageSwagger, TestEnvironment, UnitSuite}
import org.mockito.ArgumentCaptor
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.postgresql.util.PSQLException
import org.scalatra.servlet.FileItem
import org.scalatra.test.Uploadable
import org.scalatra.test.scalatest.ScalatraSuite

import scala.util.{Failure, Success}

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

  override def beforeEach(): Unit = reset(writeService)

  case class PretendFile(content: Array[Byte], contentType: String, fileName: String) extends Uploadable {
    override def contentLength: Long = content.length
  }

  val sampleUploadFile = PretendFile(Array[Byte](-1, -40, -1), "image/jpeg", "image.jpg")

  val sampleNewImageMeta =
    """
      |{
      |    "externalId": "123abc",
      |    "titles": [{
      |        "title": "Utedo med hjerte på døra",
      |        "language": "nb"
      |    }],
      |    "alttexts": [{
      |        "alttext": "En skeiv utedodør med utskåret hjerte. Foto.",
      |        "language": "nb"
      |    }],
      |    "tags": [{
      |        "tags": ["noen", "tags", "her"],
      |        "language": "nb"
      |    }],
      |    "captions": [{
      |        "caption": "En caption",
      |        "language": "nb"
      |    }],
      |    "copyright": {
      |        "origin": "http://www.scanpix.no",
      |        "authors": [],
      |        "license": {
      |            "description": "Creative Commons Attribution-ShareAlike 2.0 Generic",
      |            "url": "https://creativecommons.org/licenses/by-sa/2.0/",
      |            "license": "by-nc-sa"
      |        }
      |    }
      |}
      |
    """.stripMargin

  test("That POST / returns 400 if parameters are missing") {
    post("/", Map("metadata" -> sampleNewImageMeta), headers = Map("Authorization" -> authHeaderWithWriteRole)) {
      status should equal(400)
    }
  }


  test("That POST / returns 200 if everything went well") {
    val titles: Seq[ImageTitle] = Seq()
    val alttexts: Seq[ImageAltText] = Seq()
    val copyright = Copyright(License("by", "description", None), "", Seq.empty)
    val tags: Seq[ImageTag] = Seq()
    val captions: Seq[ImageCaption] = Seq()

    val sampleImageMeta = ImageMetaInformation(Some(1), titles, alttexts, "http://some.url/img.jpg", 1024, "image/jpeg", copyright, tags, captions, "updatedBy", new Date())

    when(writeService.storeNewImage(any[NewImageMetaInformation], any[FileItem])).thenReturn(Success(sampleImageMeta))

    post("/", Map("metadata" -> sampleNewImageMeta), Map("file" -> sampleUploadFile), headers = Map("Authorization" -> authHeaderWithWriteRole)) {
      status should equal(200)
    }
  }

  test("That POST / with language 'nb' transforms the language to 'nob'") {
    when(writeService.storeNewImage(any[NewImageMetaInformation], any[FileItem])).thenReturn(Success(mock[ImageMetaInformation]))
    val argumentCaptor = ArgumentCaptor.forClass(classOf[NewImageMetaInformation])
    post("/", Map("metadata" -> sampleNewImageMeta), Map("file" -> sampleUploadFile), headers = Map("Authorization" -> authHeaderWithWriteRole)) {
      status should equal(200)
      verify(writeService).storeNewImage(argumentCaptor.capture(), any[FileItem])
      argumentCaptor.getValue.titles.map(_.language).distinct should be (Seq("nob"))
      argumentCaptor.getValue.alttexts.map(_.language).distinct should be (Seq("nob"))
      argumentCaptor.getValue.tags.get.map(_.language).distinct should be (Seq("nob"))
      argumentCaptor.getValue.captions.get.map(_.language).distinct should be (Seq("nob"))
    }
  }

  test("That POST / returns 403 if no auth-header") {
    post("/", Map("metadata" -> sampleNewImageMeta)) {
      status should equal(403)
    }
  }

  test("That POST / returns 403 if auth header does not have expected role") {
    post("/", Map("metadata" -> sampleNewImageMeta), headers = Map("Authorization" -> authHeaderWithWrongRole)) {
      status should equal(403)
    }
  }

  test("That POST / returns 403 if auth header does not have any roles") {
    post("/", Map("metadata" -> sampleNewImageMeta), headers = Map("Authorization" -> authHeaderWithoutAnyRoles)) {
      status should equal(403)
    }
  }

  test("That POST / returns 413 if file is too big") {
    val content: Array[Byte] = Array.fill(MaxImageFileSizeBytes + 1) {
      0
    }
    post("/", Map("metadata" -> sampleNewImageMeta), Map("file" -> sampleUploadFile.copy(content)), headers = Map("Authorization" -> authHeaderWithWriteRole)) {
      status should equal(413)
    }
  }

  test("That POST / returns 409 if an image with given external_id already exists") {
    val duplicateKeyException = mock[PSQLException]
    when(duplicateKeyException.getMessage).thenReturn("ERROR: duplicate key value violates unique constraint \"cst_uni_external_id\"")
    when(writeService.storeNewImage(any[NewImageMetaInformation], any[FileItem])).thenReturn(Failure(duplicateKeyException))
    post("/", Map("metadata" -> sampleNewImageMeta), Map("file" -> sampleUploadFile), headers = Map("Authorization" -> authHeaderWithWriteRole)) {
      status should equal(409)
    }
  }

  test("That POST / returns 500 if db insertion fails with a PSQLException that doesn't have to do with unique constraint violations") {
    val duplicateKeyException = mock[PSQLException]
    when(duplicateKeyException.getMessage).thenReturn("ERROR: something completely different went wrong")
    when(writeService.storeNewImage(any[NewImageMetaInformation], any[FileItem])).thenReturn(Failure(duplicateKeyException))
    post("/", Map("metadata" -> sampleNewImageMeta), Map("file" -> sampleUploadFile), headers = Map("Authorization" -> authHeaderWithWriteRole)) {
      status should equal(500)
    }
  }

  test("That POST / returns 500 if an unexpected error occurs") {
    when(writeService.storeNewImage(any[NewImageMetaInformation], any[FileItem])).thenReturn(Failure(mock[RuntimeException]))
    post("/", Map("metadata" -> sampleNewImageMeta), Map("file" -> sampleUploadFile), headers = Map("Authorization" -> authHeaderWithWriteRole)) {
      status should equal(500)
    }
  }

}
