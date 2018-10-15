/*
 * Part of NDLA image_api.
 * Copyright (C) 2017 NDLA
 *
 * See LICENSE
 *
 */

package no.ndla.imageapi.controller

import java.util.Date

import io.digitallibrary.language.model.LanguageTag
import io.digitallibrary.license.model.{License, LicenseList}
import no.ndla.imageapi.ImageApiProperties.MaxImageFileSizeBytes
import no.ndla.imageapi.model.api.{NewImageMetaInformationV2, SearchResult, UpdateImageMetaInformation}
import no.ndla.imageapi.model.domain.{Sort, _}
import no.ndla.imageapi.model.api.StoredParameters
import no.ndla.imageapi.model.api.RawImageQueryParameters
import no.ndla.imageapi.model.{ImageNotFoundException, api}
import no.ndla.imageapi.{ImageSwagger, TestData, TestEnvironment, UnitSuite}
import org.json4s.{DefaultFormats, Formats}
import org.json4s.native.JsonParser
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatra.servlet.FileItem
import org.scalatra.test.Uploadable
import org.scalatra.test.scalatest.ScalatraSuite
import scalikejdbc.DBSession

import scala.util.{Failure, Success, Try}

class ImageControllerV2Test extends UnitSuite with ScalatraSuite with TestEnvironment {

  val jwtHeader = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9"

  val jwtClaims = "eyJodHRwczovL2RpZ2l0YWxsaWJyYXJ5LmlvL2dkbF9pZCI6ImFiYzEyMyIsImh0dHBzOi8vZGlnaXRhbGxpYnJhcnkuaW8vdXNlcl9uYW1lIjoiRG9uYWxkIER1Y2siLCJpc3MiOiJodHRwczovL3NvbWUtZG9tYWluLyIsInN1YiI6Imdvb2dsZS1vYXV0aDJ8MTIzIiwiYXVkIjoiYWJjIiwiZXhwIjoxNDg2MDcwMDYzLCJpYXQiOjE0ODYwMzQwNjMsInNjb3BlIjoiaW1hZ2VzLWxvY2FsOndyaXRlIn0"
  val jwtClaimsNoRoles = "eyJodHRwczovL2RpZ2l0YWxsaWJyYXJ5LmlvL2dkbF9pZCI6ImFiYzEyMyIsIm5hbWUiOiJEb25hbGQgRHVjayIsImlzcyI6Imh0dHBzOi8vc29tZS1kb21haW4vIiwic3ViIjoiZ29vZ2xlLW9hdXRoMnwxMjMiLCJhdWQiOiJhYmMiLCJleHAiOjE0ODYwNzAwNjMsImlhdCI6MTQ4NjAzNDA2M30"
  val jwtClaimsWrongRole = "eyJodHRwczovL2RpZ2l0YWxsaWJyYXJ5LmlvL2dkbF9pZCI6ImFiYzEyMyIsIm5hbWUiOiJEb25hbGQgRHVjayIsImlzcyI6Imh0dHBzOi8vc29tZS1kb21haW4vIiwic3ViIjoiZ29vZ2xlLW9hdXRoMnwxMjMiLCJhdWQiOiJhYmMiLCJleHAiOjE0ODYwNzAwNjMsImlhdCI6MTQ4NjAzNDA2Mywic2NvcGUiOiJpbWFnZTpyZWFkIn0"

  val authHeaderWithWriteRole = s"Bearer $jwtHeader.$jwtClaims.nAJRL8tPJSoX9wgsQ2znSAG7f8geW-gltofJm4YWdV4"
  val authHeaderWithoutAnyRoles = s"Bearer $jwtHeader.$jwtClaimsNoRoles.eNEK5datycKuV292kOxT4IhCMvrrq0KpSyH8C69mdnM"
  val authHeaderWithWrongRole = s"Bearer $jwtHeader.$jwtClaimsWrongRole.VxqM2bu2UF8IAalibIgdRdmsTDDWKEYpKzHPbCJcFzA"

  val nob = LanguageTag("nb")

  implicit val swagger: ImageSwagger = new ImageSwagger
  override val converterService = new ConverterService
  lazy val controller = new ImageControllerV2
  addServlet(controller, "/*")

  case class PretendFile(content: Array[Byte], contentType: String, fileName: String) extends Uploadable {
    override def contentLength: Long = content.length
  }

  val sampleUploadFile = PretendFile(Array[Byte](-1, -40, -1), "image/jpeg", "image.jpg")

  val sampleNewImageMetaV2: String =
    """
      |{
      |  "externalId": "ext1",
      |  "title":"test1",
      |  "alttext":"test2",
      |  "copyright": {
      |    "license": {
      |    "license": "by-sa",
      |    "description": "Creative Commons Attribution-ShareAlike 2.0 Generic",
      |    "url": "https:\/\/creativecommons.org\/licenses\/by-sa\/2.0\/"
      |  },
      |    "origin": "",
      |    "authors": [
      |  {
      |    "type": "Forfatter",
      |    "name": "Wenche Heir"
      |  }
      |    ]
      |  },
      |  "tags": [
      |    "lel"
      |  ],
      |  "caption": "captionheredude",
      |  "language": "no"
      |}
    """.stripMargin

  val sampleUpdateImageMeta: String =
    """
      |{
      | "title":"TestTittel",
      | "alttext":"TestAltText",
      | "language":"nb"
      |}
    """.stripMargin

  test("That GET / returns body and 200") {
    val expectedBody = """{"totalCount":0,"page":1,"pageSize":10,"language":"nb","results":[]}"""
    when(searchService.all(Option(any[Int]), Option(any[String]), Option(any[LanguageTag]), any[Sort.Value], Option(any[Int]), Option(any[Int]))).thenReturn(SearchResult(0, 1, 10, "nb", List()))
    get("/") {
      status should equal(200)
      body should equal(expectedBody)
    }
  }

  test("That GET / returns body and 200 when image exists") {

    val imageSummary = api.ImageMetaSummary("4", api.ImageTitle("Tittel", nob), Seq("Jason Bourne", "Ben Affleck"), api.ImageAltText("AltText", nob), "http://image-api.ndla-local/image-api/raw/4", "http://image-api.ndla-local/image-api/v2/images/4", "by-sa", Seq(nob))
    val expectedBody = """{"totalCount":1,"page":1,"pageSize":10,"language":"nb","results":[{"id":"4","title":{"title":"Tittel","language":"nb"},"contributors":["Jason Bourne","Ben Affleck"],"altText":{"alttext":"AltText","language":"nb"},"previewUrl":"http://image-api.ndla-local/image-api/raw/4","metaUrl":"http://image-api.ndla-local/image-api/v2/images/4","license":"by-sa","supportedLanguages":["nb"]}]}"""
    when(searchService.all(Option(any[Int]), Option(any[String]), Option(any[LanguageTag]), any[Sort.Value], Option(any[Int]), Option(any[Int]))).thenReturn(SearchResult(1, 1, 10, "nb", List(imageSummary)))
    get("/") {
      status should equal(200)
      body should equal(expectedBody)
    }
  }

  test("That GET /<id> returns 404 when image does not exist") {
    when(imageRepository.withId(123)).thenReturn(None)
    get("/123") {
      status should equal(404)
    }
  }

  test("That GET /<id> returns body and 200 when image exists") {
    implicit val formats: Formats = DefaultFormats + new LanguageTagSerializer
    val testUrl = "http://test.test/1"
    val expectedBody = s"""{"id":"1","metaUrl":"$testUrl","title":{"title":"Elg i busk","language":"nb"},"alttext":{"alttext":"Elg i busk","language":"nb"},"imageUrl":"$testUrl","size":2865539,"contentType":"image/jpeg","copyright":{"license":{"license":"CC-BY-NC-SA-4.0","description":"Creative Commons Attribution Non Commercial Share Alike 4.0 International","url":"http://creativecommons.org/licenses/by-nc-sa/4.0/legalcode"},"origin":"http://www.scanpix.no","creators":[{"type":"Fotograf","name":"Test Testesen"}],"processors":[{"type":"Redaksjonelt","name":"Kåre Knegg"}],"rightsholders":[{"type":"Leverandør","name":"Leverans Leveransensen"}]},"tags":{"tags":["rovdyr","elg"],"language":"nb"},"caption":{"caption":"Elg i busk","language":"nb"},"supportedLanguages":["nb"]}"""
    val expectedObject = JsonParser.parse(expectedBody).extract[api.ImageMetaInformationV2]
    when(imageRepository.withId(1)).thenReturn(Option(TestData.elg))
    when(imageRepository.getImageVariants(any[String])(any[DBSession])).thenReturn(Map[String,ImageVariant]())

    get("/1") {
      status should equal(200)
      val result = JsonParser.parse(body).extract[api.ImageMetaInformationV2]
      result.copy(imageUrl = testUrl, metaUrl = testUrl) should equal(expectedObject)
    }
  }

  test("That GET /<id> returns body with original copyright if agreement doesnt exist") {
    implicit val formats: Formats = DefaultFormats + new LanguageTagSerializer
    val testUrl = "http://test.test/1"
    val expectedBody = s"""{"id":"1","metaUrl":"$testUrl","title":{"title":"Elg i busk","language":"nb"},"alttext":{"alttext":"Elg i busk","language":"nb"},"imageUrl":"$testUrl","size":2865539,"contentType":"image/jpeg","copyright":{"license":{"license":"CC-BY-NC-SA-4.0","description":"Creative Commons Attribution Non Commercial Share Alike 4.0 International","url":"http://creativecommons.org/licenses/by-nc-sa/4.0/legalcode"}, "agreementId":1, "origin":"http://www.scanpix.no","creators":[{"type":"Fotograf","name":"Test Testesen"}],"processors":[{"type":"Redaksjonelt","name":"Kåre Knegg"}],"rightsholders":[{"type":"Leverandør","name":"Leverans Leveransensen"}]},"tags":{"tags":["rovdyr","elg"],"language":"nb"},"caption":{"caption":"Elg i busk","language":"nb"},"supportedLanguages":["nb"]}"""
    val expectedObject = JsonParser.parse(expectedBody).extract[api.ImageMetaInformationV2]
    val agreementElg = ImageMetaInformation(Some(1), None, List(ImageTitle("Elg i busk", nob)), List(ImageAltText("Elg i busk", nob)),
      "Elg.jpg", 2865539, "image/jpeg",
      Copyright(TestData.ByNcSa, "http://www.scanpix.no", List(Author("Fotograf", "Test Testesen")), List(Author("Redaksjonelt", "Kåre Knegg")), List(Author("Leverandør", "Leverans Leveransensen")), Some(1), None, None),
      List(ImageTag(List("rovdyr", "elg"), nob)), List(ImageCaption("Elg i busk", nob)), "ndla124", TestData.updated(), Some(StorageService.CLOUDINARY))

    when(imageRepository.withId(1)).thenReturn(Option(agreementElg))
    when(imageRepository.getImageVariants(any[String])(any[DBSession])).thenReturn(Map[String,ImageVariant]())

    get("/1") {
      status should equal(200)
      val result = JsonParser.parse(body).extract[api.ImageMetaInformationV2]
      result.copy(imageUrl = testUrl, metaUrl = testUrl) should equal(expectedObject)
    }
  }

  test("That POST / returns 403 if no auth-header") {
    post("/", Map("metadata" -> sampleNewImageMetaV2)) {
      status should equal(403)
    }
  }

  test("That POST / returns 400 if parameters are missing") {
    post("/", Map("metadata" -> sampleNewImageMetaV2), headers = Map("Authorization" -> authHeaderWithWriteRole)) {
      status should equal(400)
    }
  }

  test("That POST / returns 200 if everything went well") {
    val titles: Seq[ImageTitle] = Seq()
    val alttexts: Seq[ImageAltText] = Seq()
    val copyright = Copyright(License("cc-by-2.0"), "", Seq.empty, Seq.empty, Seq.empty, None, None, None)
    val tags: Seq[ImageTag] = Seq()
    val captions: Seq[ImageCaption] = Seq()

    val sampleImageMeta = ImageMetaInformation(Some(1), None, titles, alttexts, "http://some.url/img.jpg", 1024, "image/jpeg", copyright, tags, captions, "updatedBy", new Date(), Some(StorageService.CLOUDINARY))

    when(imageRepository.withExternalId("ext1")).thenReturn(None)
    when(writeService.storeNewImage(any[NewImageMetaInformationV2], any[FileItem])).thenReturn(Success(sampleImageMeta))

    post("/", Map("metadata" -> sampleNewImageMetaV2), Map("file" -> sampleUploadFile), headers = Map("Authorization" -> authHeaderWithWriteRole)) {
      status should equal(200)
    }
  }

  test("That POST / returns 403 if auth header does not have expected role") {
    post("/", Map("metadata" -> sampleNewImageMetaV2), headers = Map("Authorization" -> authHeaderWithWrongRole)) {
      status should equal(403)
    }
  }

  test("That POST / returns 403 if auth header does not have any roles") {
    post("/", Map("metadata" -> sampleNewImageMetaV2), headers = Map("Authorization" -> authHeaderWithoutAnyRoles)) {
      status should equal(403)
    }
  }

  test("That POST / returns 409 if an image with same externalId already exists") {
    when(imageRepository.withExternalId("ext1")).thenReturn(Some(mock[ImageMetaInformation]))
    post("/", Map("metadata" -> sampleNewImageMetaV2), Map("file" -> sampleUploadFile), headers = Map("Authorization" -> authHeaderWithWriteRole)) {
      status should equal(409)
    }
  }

  test("That POST / returns 413 if file is too big") {
    val content: Array[Byte] = Array.fill(MaxImageFileSizeBytes + 1) {
      0
    }
    post("/", Map("metadata" -> sampleNewImageMetaV2), Map("file" -> sampleUploadFile.copy(content)), headers = Map("Authorization" -> authHeaderWithWriteRole)) {
      status should equal(413)
    }
  }

  test("That POST / returns 500 if an unexpected error occurs") {
    when(imageRepository.withExternalId("ext1")).thenReturn(None)
    when(writeService.storeNewImage(any[NewImageMetaInformationV2], any[FileItem])).thenReturn(Failure(mock[RuntimeException]))

    post("/", Map("metadata" -> sampleNewImageMetaV2), Map("file" -> sampleUploadFile), headers = Map("Authorization" -> authHeaderWithWriteRole)) {
      status should equal(500)
    }
  }

  test("That PATCH /<id> returns 200 when everything went well") {
    reset(writeService)
    when(writeService.updateImage(any[Long], any[UpdateImageMetaInformation])).thenReturn(Try(TestData.apiElg))
    patch("/1", sampleUpdateImageMeta, headers = Map("Authorization" -> authHeaderWithWriteRole)) {
      status should equal(200)
    }
  }

  test("That PATCH /<id> returns 404 when image doesn't exist") {
    reset(writeService)
    when(writeService.updateImage(any[Long], any[UpdateImageMetaInformation])).thenThrow(new ImageNotFoundException(s"Image with id 1 not found"))
    patch("/1", sampleUpdateImageMeta, headers = Map("Authorization" -> authHeaderWithWriteRole)) {
      status should equal(404)
    }
  }

  test("That PATCH /<id> returns 403 when not permitted") {
    patch("/1", Map("metadata" -> sampleUpdateImageMeta), headers = Map("Authorization" -> authHeaderWithoutAnyRoles)) {
      status should equal(403)
    }
  }

  test("That GET /licenses returns a list of licenses and 200") {
    implicit val formats: Formats = DefaultFormats
    val expectedObject = LicenseList.licenses.map(licenseDefinition => api.License(licenseDefinition.name, licenseDefinition.description, Some(licenseDefinition.url)))

    get("/licenses") {
      status should equal(200)
      val resultObject = JsonParser.parse(body).extract[Seq[api.License]]
      resultObject should equal(expectedObject)
    }
  }
}
