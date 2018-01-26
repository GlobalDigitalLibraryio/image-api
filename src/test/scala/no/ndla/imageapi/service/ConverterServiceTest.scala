/*
 * Part of NDLA image_api.
 * Copyright (C) 2016 NDLA
 *
 * See LICENSE
 *
 */

package no.ndla.imageapi.service

import java.util.Date
import javax.servlet.http.HttpServletRequest

import io.digitallibrary.language.model.LanguageTag
import no.ndla.imageapi.model.domain._
import no.ndla.imageapi.{ImageApiProperties, TestEnvironment, UnitSuite}
import io.digitallibrary.network.ApplicationUrl
import org.joda.time.{DateTime, DateTimeZone}
import org.mockito.Mockito._

class ConverterServiceTest extends UnitSuite with TestEnvironment {

  override val converterService = new ConverterService

  val updated: Date = new DateTime(2017, 4, 1, 12, 15, 32, DateTimeZone.UTC).toDate

  val full = Image("/123.png", 200, "image/png")
  val DefaultImageMetaInformation = ImageMetaInformation(Some(1), List(ImageTitle("test", LanguageTag("nb"))), List(), full.fileName, full.size, full.contentType, Copyright(License("", "", None), "", List()), List(), List(), "ndla124", updated)
  val english = LanguageTag("eng")

  override def beforeEach: Unit = {
    val request = mock[HttpServletRequest]
    when(request.getServerPort).thenReturn(80)
    when(request.getScheme).thenReturn("http")
    when(request.getServerName).thenReturn("image-api")
    when(request.getServletPath).thenReturn("/v1/images")

    ApplicationUrl.set(request)
  }

  def setApplicationUrl(): Unit = {
    val request = mock[HttpServletRequest]
    when(request.getServerPort).thenReturn(80)
    when(request.getScheme).thenReturn("http")
    when(request.getServerName).thenReturn("image-api")
    when(request.getServletPath).thenReturn("/v2/images")

    ApplicationUrl.set(request)
  }

  override def afterEach: Unit = {
    ApplicationUrl.clear()
  }

  test("That asApiImageMetaInformationWithApplicationUrl returns links with applicationUrl") {
    val api = converterService.asApiImageMetaInformationWithApplicationUrl(DefaultImageMetaInformation)
    api.metaUrl should equal ("http://image-api/v1/images/1")
    api.imageUrl should equal ("http://local.digitallibrary.io/image-api/raw/123.png")
  }

  test("That asApiImageMetaInformationWithApplicationUrl returns links with applicationUrl, also when url lacks leading slash") {
    val api = converterService.asApiImageMetaInformationWithApplicationUrl(DefaultImageMetaInformation.copy(imageUrl = "123.png"))
    api.metaUrl should equal ("http://image-api/v1/images/1")
    api.imageUrl should equal ("http://local.digitallibrary.io/image-api/raw/123.png")
  }

  test("That asApiImageMetaInformationWithDomainUrl returns links with domain urls") {
    val api = converterService.asApiImageMetaInformationWithDomainUrl(DefaultImageMetaInformation)
    api.metaUrl should equal (s"${ImageApiProperties.ImageApiUrlBase}1")
    api.imageUrl should equal ("http://local.digitallibrary.io/image-api/raw/123.png")
  }

  test("That asApiImageMetaInformationWithApplicationUrlAndSingleLanguage returns links with applicationUrl") {
    setApplicationUrl()

    val api = converterService.asApiImageMetaInformationWithApplicationUrlAndSingleLanguage(DefaultImageMetaInformation, english)
    api.get.metaUrl should equal ("http://image-api/v2/images/1")
    api.get.imageUrl should equal ("http://local.digitallibrary.io/image-api/raw/123.png")
  }

  test("That asApiImageMetaInformationWithDomainUrlAndSingleLanguage returns links with domain urls") {
    setApplicationUrl()

    val api = converterService.asApiImageMetaInformationWithDomainUrlAndSingleLanguage(DefaultImageMetaInformation, english)
    api.get.metaUrl should equal ("http://local.digitallibrary.io/image-api/v2/images/1")
    api.get.imageUrl should equal ("http://local.digitallibrary.io/image-api/raw/123.png")
  }

  test("That asApiImageMetaInformationWithApplicationUrlAndSingleLanguage returns links even if language is not supported") {
    setApplicationUrl()
    val api = converterService.asApiImageMetaInformationWithApplicationUrlAndSingleLanguage(DefaultImageMetaInformation, english)

    api.get.metaUrl should equal ("http://image-api/v2/images/1")
    api.get.imageUrl should equal ("http://local.digitallibrary.io/image-api/raw/123.png")
  }

  test("That asApiImageMetaInformationWithDomainUrlAndSingleLanguage returns links even if language is not supported") {
    setApplicationUrl()

    val api = converterService.asApiImageMetaInformationWithDomainUrlAndSingleLanguage(DefaultImageMetaInformation, english)
    api.get.metaUrl should equal ("http://local.digitallibrary.io/image-api/v2/images/1")
    api.get.imageUrl should equal ("http://local.digitallibrary.io/image-api/raw/123.png")
  }
}
