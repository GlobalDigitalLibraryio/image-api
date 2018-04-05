/*
 * Part of NDLA image_api.
 * Copyright (C) 2016 NDLA
 *
 * See LICENSE
 *
 */

package no.ndla.imageapi.service

import java.io.{BufferedInputStream, InputStream}
import java.util.Date

import io.digitallibrary.language.model.LanguageTag
import io.digitallibrary.network.ApplicationUrl
import javax.servlet.http.HttpServletRequest
import no.ndla.imageapi.model.api._
import no.ndla.imageapi.model.domain.{Image, ImageMetaInformation}
import no.ndla.imageapi.model.{ValidationException, domain}
import no.ndla.imageapi.{TestData, TestEnvironment, UnitSuite}
import org.joda.time.{DateTime, DateTimeZone}
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatra.servlet.FileItem
import scalikejdbc.DBSession

import scala.util.{Failure, Success}

class WriteServiceTest extends UnitSuite with TestEnvironment {
  override val writeService = new WriteService
  override val converterService = new ConverterService
  val newFileName = "AbCdeF.mp3"
  val fileMock1: FileItem = mock[FileItem]

  val newImageMeta = NewImageMetaInformationV2(
    "title",
    "alt text",
    Copyright(License("by", "", None), "", Seq.empty, Seq.empty, Seq.empty, None, None, None),
    Seq.empty,
    "",
    LanguageTag("en")
  )

  def updated(): Date = (new DateTime(2017, 4, 1, 12, 15, 32, DateTimeZone.UTC)).toDate

  val domainImageMeta: ImageMetaInformation = converterService.asDomainImageMetaInformationV2(newImageMeta, Image(newFileName, 1024, "image/jpeg"))

  override def beforeEach: Unit = {
    when(fileMock1.getContentType).thenReturn(Some("image/jpeg"))
    when(fileMock1.get).thenReturn(Array[Byte](-1, -40, -1))
    when(fileMock1.size).thenReturn(1024)
    when(fileMock1.name).thenReturn("file.jpg")

    val applicationUrl = mock[HttpServletRequest]
    when(applicationUrl.getHeader(any[String])).thenReturn("http")
    when(applicationUrl.getServerName).thenReturn("localhost")
    when(applicationUrl.getServletPath).thenReturn("/image-api/v2/images/")
    ApplicationUrl.set(applicationUrl)

    reset(imageRepository, indexService, imageStorage)
    when(imageRepository.insert(any[ImageMetaInformation], any[Option[String]])(any[DBSession])).thenReturn(domainImageMeta.copy(id=Some(1)))
  }

  test("uploadFile should return Success if file upload succeeds") {
    when(imageStorage.objectExists(any[String])).thenReturn(false)
    when(imageStorage.uploadFromStream(any[InputStream], any[String], any[String], any[Long])).thenReturn(Success(newFileName))
    val result = writeService.uploadImage(fileMock1)
    verify(imageStorage, times(1)).uploadFromStream(any[InputStream], any[String], any[String], any[Long])

    result should equal(Success(Image(newFileName, 1024, "image/jpeg")))
  }

  test("uploadFile should return Failure if file upload failed") {
    when(imageStorage.objectExists(any[String])).thenReturn(false)
    when(imageStorage.uploadFromStream(any[InputStream], any[String], any[String], any[Long])).thenReturn(Failure(new RuntimeException))

    writeService.uploadImage(fileMock1).isFailure should be (true)
  }

  test("storeNewImage should return Failure if upload failes") {
    when(validationService.validateImageFile(any[FileItem])).thenReturn(None)
    when(imageStorage.uploadFromStream(any[InputStream], any[String], any[String], any[Long])).thenReturn(Failure(new RuntimeException))

    writeService.storeNewImage(newImageMeta, fileMock1).isFailure should be (true)
  }

  test("storeNewImage should return Failure if validation fails") {
    when(validationService.validateImageFile(any[FileItem])).thenReturn(None)
    when(validationService.validate(any[ImageMetaInformation])).thenReturn(Failure(new ValidationException(errors=Seq())))
    when(imageStorage.uploadFromStream(any[InputStream], any[String], any[String], any[Long])).thenReturn(Success(newFileName))

    writeService.storeNewImage(newImageMeta, fileMock1).isFailure should be (true)
    verify(imageRepository, times(0)).insert(any[ImageMetaInformation], any[Option[String]])(any[DBSession])
    verify(indexService, times(0)).indexDocument(any[ImageMetaInformation])
    verify(imageStorage, times(1)).deleteObject(any[String])
  }

  test("storeNewImage should return Failure if failed to insert into database") {
    when(validationService.validateImageFile(any[FileItem])).thenReturn(None)
    when(validationService.validate(any[ImageMetaInformation])).thenReturn(Success(domainImageMeta))
    when(imageStorage.uploadFromStream(any[InputStream], any[String], any[String], any[Long])).thenReturn(Success(newFileName))
    when(imageRepository.insert(any[ImageMetaInformation], any[Option[String]])(any[DBSession])).thenThrow(new RuntimeException)

    writeService.storeNewImage(newImageMeta, fileMock1).isFailure should be (true)
    verify(indexService, times(0)).indexDocument(any[ImageMetaInformation])
    verify(imageStorage, times(1)).deleteObject(any[String])
  }

  test("storeNewImage should return Failure if failed to index image metadata") {
    when(validationService.validateImageFile(any[FileItem])).thenReturn(None)
    when(validationService.validate(any[ImageMetaInformation])).thenReturn(Success(domainImageMeta))
    when(imageStorage.uploadFromStream(any[InputStream], any[String], any[String], any[Long])).thenReturn(Success(newFileName))
    when(indexService.indexDocument(any[ImageMetaInformation])).thenReturn(Failure(new RuntimeException))

    writeService.storeNewImage(newImageMeta, fileMock1).isFailure should be (true)
    verify(imageRepository, times(1)).insert(any[ImageMetaInformation], any[Option[String]])(any[DBSession])
    verify(imageStorage, times(1)).deleteObject(any[String])
  }

  test("storeNewImage should return Success if creation of new image file succeeded") {
    val afterInsert = domainImageMeta.copy(id=Some(1))
    when(validationService.validateImageFile(any[FileItem])).thenReturn(None)
    when(validationService.validate(any[ImageMetaInformation])).thenReturn(Success(domainImageMeta))
    when(imageStorage.uploadFromStream(any[InputStream], any[String], any[String], any[Long])).thenReturn(Success(newFileName))
    when(indexService.indexDocument(any[ImageMetaInformation])).thenReturn(Success(afterInsert))

    val result = writeService.storeNewImage(newImageMeta, fileMock1)
    result.isSuccess should be (true)
    result should equal(Success(afterInsert))

    verify(imageRepository, times(1)).insert(any[ImageMetaInformation], any[Option[String]])(any[DBSession])
    verify(indexService, times(1)).indexDocument(any[ImageMetaInformation])
  }

  test("filenameToHashFilename returns a filename with the right extension") {
    writeService.filenameToHashFilename("image.jpg", "abcd") should equal("abcd.jpg")
    writeService.filenameToHashFilename("ima.ge.jpg", "abcd") should equal("abcd.jpg")
    writeService.filenameToHashFilename(".jpg", "abcd") should equal("abcd.jpg")
  }

  test("converter to domain should set updatedBy from authUser and updated date"){
    when(authUser.userOrClientid()).thenReturn("ndla54321")
    when(clock.now()).thenReturn(updated())
    val domain = converterService.asDomainImageMetaInformationV2(newImageMeta, Image(newFileName, 1024, "image/jpeg"))
    domain.updatedBy should equal ("ndla54321")
    domain.updated should equal(updated())
  }

  test("That mergeLanguageFields returns original list when updated is empty") {
    val existing = Seq(domain.ImageTitle("Tittel 1", LanguageTag("nb")), domain.ImageTitle("Tittel 2", LanguageTag("nn")), domain.ImageTitle("Tittel 3", LanguageTag("und")))
    writeService.mergeLanguageFields(existing, Seq()) should equal(existing)
  }

  test("That mergeLanguageFields updated the english title only when specified") {
    val tittel1 = domain.ImageTitle("Tittel 1", LanguageTag("nb"))
    val tittel2 = domain.ImageTitle("Tittel 2", LanguageTag("nn"))
    val tittel3 = domain.ImageTitle("Tittel 3", LanguageTag("en"))
    val oppdatertTittel3 = domain.ImageTitle("Title 3 in english", LanguageTag("en"))

    val existing = Seq(tittel1, tittel2, tittel3)
    val updated = Seq(oppdatertTittel3)

    writeService.mergeLanguageFields(existing, updated) should equal(Seq(tittel1, tittel2, oppdatertTittel3))
  }

  test("That mergeLanguageFields removes a title that is empty") {
    val tittel1 = domain.ImageTitle("Tittel 1", LanguageTag("nb"))
    val tittel2 = domain.ImageTitle("Tittel 2", LanguageTag("nn"))
    val tittel3 = domain.ImageTitle("Tittel 3", LanguageTag("en"))
    val tittelToRemove = domain.ImageTitle("", LanguageTag("nn"))

    val existing = Seq(tittel1, tittel2, tittel3)
    val updated = Seq(tittelToRemove)

    writeService.mergeLanguageFields(existing, updated) should equal(Seq(tittel1, tittel3))
  }

  test("That mergeLanguageFields updates the title with unknown language specified") {
    val tittel1 = domain.ImageTitle("Tittel 1", LanguageTag("nb"))
    val tittel2 = domain.ImageTitle("Tittel 2", LanguageTag("und"))
    val tittel3 = domain.ImageTitle("Tittel 3", LanguageTag("en"))
    val oppdatertTittel2 = domain.ImageTitle("Tittel 2 er oppdatert", LanguageTag("und"))

    val existing = Seq(tittel1, tittel2, tittel3)
    val updated = Seq(oppdatertTittel2)

    writeService.mergeLanguageFields(existing, updated) should equal(Seq(tittel1, tittel3, oppdatertTittel2))
  }

  test("That mergeLanguageFields also updates the correct content") {
    val desc1 = domain.ImageAltText("Beskrivelse 1", LanguageTag("nb"))
    val desc2 = domain.ImageAltText("Beskrivelse 2", LanguageTag("und"))
    val desc3 = domain.ImageAltText("Beskrivelse 3", LanguageTag("en"))
    val oppdatertDesc2 = domain.ImageAltText("Beskrivelse 2 er oppdatert", LanguageTag("und"))

    val existing = Seq(desc1, desc2, desc3)
    val updated = Seq(oppdatertDesc2)

    writeService.mergeLanguageFields(existing, updated) should equal(Seq(desc1, desc3, oppdatertDesc2))
  }

  test("mergeImages should append a new language if language not already exists") {
    val date = new Date()
    val user = "ndla124"
    val existing = TestData.elg.copy(updated = date, updatedBy=user )
    val toUpdate = UpdateImageMetaInformation(
      "en",
      Some("Title"),
      Some("AltText"),
      None,
      None,
      None
    )

    val expectedResult = existing.copy(
      titles = List(existing.titles.head, domain.ImageTitle("Title", LanguageTag("en"))),
      alttexts = List(existing.alttexts.head, domain.ImageAltText("AltText", LanguageTag("en")))
    )

    when(authUser.userOrClientid()).thenReturn(user)
    when(clock.now()).thenReturn(date)

    writeService.mergeImages(existing, toUpdate) should equal(expectedResult)
  }

  test("mergeImages overwrite a languages if specified language already exist in cover") {
    val date = new Date()
    val user = "ndla124"
    val existing = TestData.elg.copy(updated = date, updatedBy=user )
    val toUpdate = UpdateImageMetaInformation(
      "nb",
      Some("Title"),
      Some("AltText"),
      None,
      None,
      None
    )

    val expectedResult = existing.copy(
      titles = List(domain.ImageTitle("Title", LanguageTag("nb"))),
      alttexts = List(domain.ImageAltText("AltText", LanguageTag("nb")))
    )

    when(authUser.userOrClientid()).thenReturn(user)
    when(clock.now()).thenReturn(date)

    writeService.mergeImages(existing, toUpdate) should equal(expectedResult)
  }

  test("mergeImages updates optional values if specified") {
    val date = new Date()
    val user = "ndla124"
    val existing = TestData.elg.copy(updated = date, updatedBy=user )
    val toUpdate = UpdateImageMetaInformation(
      "nb",
      Some("Title"),
      Some("AltText"),
      Some(Copyright(License("testLic", "License for testing", None), "test", List(Author("Opphavsmann", "Testerud")), List(), List(), None, None, None)),
      Some(List("a", "b", "c")),
      Some("Caption")
    )

    val expectedResult = existing.copy(
      titles = List(domain.ImageTitle("Title", LanguageTag("nb"))),
      alttexts = List(domain.ImageAltText("AltText", LanguageTag("nb"))),
      copyright = domain.Copyright(domain.License("testLic", "License for testing", None), "test", List(domain.Author("Opphavsmann", "Testerud")), List(), List(), None, None, None),
      tags = List(domain.ImageTag(List("a", "b", "c"), LanguageTag("nb"))),
      captions = List(domain.ImageCaption("Caption", LanguageTag("nb")))
    )

    when(authUser.userOrClientid()).thenReturn(user)
    when(clock.now()).thenReturn(date)

    writeService.mergeImages(existing, toUpdate) should equal(expectedResult)
  }
  
  test("MD5 hashing works as expected") {
    val bis = new BufferedInputStream(TestData.NdlaLogoImage.stream)
    val bytes = Stream.continually(bis.read).takeWhile(p => p != -1).map(_.toByte).toArray
    writeService.md5Hash(bytes) should equal ("d8a1cf806a7ebf443fa161e274ad8706")
  }

}
