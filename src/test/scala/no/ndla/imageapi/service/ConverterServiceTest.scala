/*
 * Part of NDLA image_api.
 * Copyright (C) 2016 NDLA
 *
 * See LICENSE
 *
 */

package no.ndla.imageapi.service

import java.util.Date

import io.digitallibrary.language.model.LanguageTag
import io.digitallibrary.network.ApplicationUrl
import javax.servlet.http.HttpServletRequest
import no.ndla.imageapi.model.api
import no.ndla.imageapi.model.domain._
import no.ndla.imageapi.{ImageApiProperties, TestEnvironment, UnitSuite}
import org.joda.time.{DateTime, DateTimeZone}
import org.mockito.Mockito._

class ConverterServiceTest extends UnitSuite with TestEnvironment {

  override val converterService = new ConverterService

  val updated: Date = new DateTime(2017, 4, 1, 12, 15, 32, DateTimeZone.UTC).toDate

  val full = Image("/123.png", 200, "image/png")
  val nob = LanguageTag("nb")
  val DefaultImageMetaInformation = ImageMetaInformation(Some(1), List(ImageTitle("test", nob)), List(), full.fileName, full.size, full.contentType, Copyright(License("", "", None), "", List(), List(), List(), None, None, None), List(), List(), "ndla124", updated)
  val MultiLangImage = ImageMetaInformation(Some(2), List(ImageTitle("nynorsk", LanguageTag("nn")), ImageTitle("english", LanguageTag("en")), ImageTitle("norsk", LanguageTag("und"))), List(), full.fileName, full.size, full.contentType, Copyright(License("", "", None), "", List(), List(), List(), None, None, None), List(), List(), "ndla124", updated)
  val english = LanguageTag("eng")

  override def beforeEach: Unit = {
    val request = mock[HttpServletRequest]
    when(request.getServerPort).thenReturn(80)
    when(request.getScheme).thenReturn("http")
    when(request.getServerName).thenReturn("image-api")
    when(request.getServletPath).thenReturn("/v2/images")

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
    api.metaUrl should equal ("http://image-api/v2/images/1")
    api.imageUrl should equal ("http://local.digitallibrary.io/image-api/raw/123.png")
  }

  test("That asApiImageMetaInformationWithApplicationUrl returns links with applicationUrl, also when url lacks leading slash") {
    val api = converterService.asApiImageMetaInformationWithApplicationUrl(DefaultImageMetaInformation.copy(imageUrl = "123.png"))
    api.metaUrl should equal ("http://image-api/v2/images/1")
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

  test("That asApiImageMetaInformationWithApplicationUrlV2 returns with agreement copyright features") {
    setApplicationUrl()
    val from = DateTime.now().minusDays(5).toDate
    val to = DateTime.now().plusDays(10).toDate
    val agreementCopyright = api.Copyright(
      api.License("gnu", "gpl", None),
      "http://tjohei.com/",
      List(),
      List(),
      List(api.Author("Supplier", "Mads LakseService")),
      None,
      Some(from),
      Some(to)
    )
    //when(draftApiClient.getAgreementCopyright(1)).thenReturn(Some(agreementCopyright))
    val apiImage = converterService.asApiImageMetaInformationWithApplicationUrlAndSingleLanguage(DefaultImageMetaInformation.copy(copyright = DefaultImageMetaInformation.copyright.copy(
      processors = List(Author("Idea", "Kaptein Snabelfant")),
      rightsholders = List(Author("Publisher", "KjeksOgKakerAS")),
      agreementId = Some(1)
    )), LanguageTag("und"))

    apiImage.get.copyright.creators.size should equal(0)
    apiImage.get.copyright.processors.head.name should equal("Kaptein Snabelfant")
    //apiImage.get.copyright.rightsholders.head.name should equal("Mads LakseService")
    apiImage.get.copyright.rightsholders.size should equal(1)
    //apiImage.get.copyright.license.license should equal("gnu")
    //apiImage.get.copyright.validFrom.get should equal(from)
    //apiImage.get.copyright.validTo.get should equal(to)
  }

  test("that asImageMetaInformationV2 properly") {
    val result1 = converterService.asApiImageMetaInformationWithDomainUrlAndSingleLanguage(MultiLangImage, LanguageTag("nb"))
    result1.get.id should be("2")
    result1.get.title.language should be(LanguageTag("en"))

    val result2 = converterService.asApiImageMetaInformationWithDomainUrlAndSingleLanguage(MultiLangImage, LanguageTag("en"))
    result2.get.id should be("2")
    result2.get.title.language should be(LanguageTag("en"))

    val result3 = converterService.asApiImageMetaInformationWithDomainUrlAndSingleLanguage(MultiLangImage, LanguageTag("nn"))
    result3.get.id should be("2")
    result3.get.title.language should be(LanguageTag("nn"))

  }

  test("that asImageMetaInformationV2 returns sorted supportedLanguages") {
    val result = converterService.asApiImageMetaInformationWithDomainUrlAndSingleLanguage(MultiLangImage, LanguageTag("nb"))
    result.get.supportedLanguages should be(Seq(LanguageTag("nn"), LanguageTag("en"), LanguageTag("und")))
  }

}
