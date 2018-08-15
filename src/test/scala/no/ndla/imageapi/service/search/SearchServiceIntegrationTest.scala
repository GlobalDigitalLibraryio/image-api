/*
 * Part of NDLA image_api.
 * Copyright (C) 2016 NDLA
 *
 * See LICENSE
 */

package no.ndla.imageapi.service.search

import java.util.Date

import io.digitallibrary.language.model.LanguageTag
import io.digitallibrary.license.model
import io.digitallibrary.network.ApplicationUrl
import javax.servlet.http.HttpServletRequest
import no.ndla.imageapi.ImageApiProperties.{DefaultPageSize, MaxPageSize}
import no.ndla.imageapi.integration.{E4sClient, EsClientFactory}
import no.ndla.imageapi.model.api
import no.ndla.imageapi.model.domain._
import no.ndla.imageapi.{ImageApiProperties, TestEnvironment, UnitSuite}
import no.ndla.tag.IntegrationTest
import org.joda.time.{DateTime, DateTimeZone}
import org.mockito.Matchers._
import org.mockito.Mockito._

@IntegrationTest
class SearchServiceIntegrationTest extends UnitSuite with TestEnvironment {

  val esPort = 9200

  override val esClient: E4sClient = EsClientFactory.getClient(searchServer = s"elasticsearch://localhost:$esPort")
  override val searchConverterService = new SearchConverterService
  override val converterService = new ConverterService
  override val indexService = new IndexService
  override val searchService = new SearchService

  val getStartAtAndNumResults: PrivateMethod[(Int, Int)] = PrivateMethod[(Int, Int)]('getStartAtAndNumResults)

  val nb = LanguageTag("nb")

  val largeImage = Image("large-full-url", 10000, "jpg")
  val smallImage = Image("small-full-url", 100, "jpg")

  val byNcSa = Copyright(model.License("cc-by-nc-sa-2.0"), "Gotham City", List(Author("Forfatter", "DC Comics")), List(), List(), None, None, None)
  val publicDomain = Copyright(model.License("cc0-1.0"), "Metropolis", List(Author("Forfatter", "Bruce Wayne")), List(), List(), None, None, None)
  val updated: Date = new DateTime(2017, 4, 1, 12, 15, 32, DateTimeZone.UTC).toDate
  val agreement1Copyright = api.Copyright(api.License("GPL-3.0", "gnustuff", Some("http://gnugnusen")), "Simsalabim", List(), List(), List(), None, None, None)

  val image1 = ImageMetaInformation(Some(1), None, List(ImageTitle("Batmen er på vift med en bil", nb)), List(ImageAltText("Bilde av en bil flaggermusmann som vifter med vingene bil.", nb)), largeImage.fileName, largeImage.size, largeImage.contentType, byNcSa, List(ImageTag(List("fugl"), nb)), List(), "ndla124", updated)
  val image2 = ImageMetaInformation(Some(2), None, List(ImageTitle("Pingvinen er ute og går", nb)), List(ImageAltText("Bilde av en en pingvin som vagger borover en gate.", nb)), largeImage.fileName, largeImage.size, largeImage.contentType, publicDomain, List(ImageTag(List("fugl"), nb)), List(), "ndla124", updated)
  val image3 = ImageMetaInformation(Some(3), None, List(ImageTitle("Donald Duck kjører bil", nb)), List(ImageAltText("Bilde av en en and som kjører en rød bil.", nb)), smallImage.fileName, smallImage.size, smallImage.contentType, byNcSa, List(ImageTag(List("and"), nb)), List(), "ndla124", updated)
  val image4 = ImageMetaInformation(Some(4), None, List(ImageTitle("Hulken er ute og lukter på blomstene", LanguageTag("und"))), Seq(), smallImage.fileName, smallImage.size, smallImage.contentType, byNcSa, Seq(), Seq(), "ndla124", updated)
  val image5 = ImageMetaInformation(Some(5), None, List(ImageTitle("Dette er et urelatert bilde", LanguageTag("und")), ImageTitle("This is a unrelated photo", LanguageTag("en")), ImageTitle("Nynoreg", LanguageTag("nn"))), Seq(ImageAltText("urelatert alttext", LanguageTag("und")), ImageAltText("Nynoreg", LanguageTag("nn"))), smallImage.fileName, smallImage.size, smallImage.contentType, byNcSa.copy(agreementId = Some(1)), Seq(), Seq(), "ndla124", updated)

  override def beforeAll(): Unit = {
    indexService.createIndexWithName(ImageApiProperties.SearchIndex)

    //when(draftApiClient.getAgreementCopyright(any[Long])).thenReturn(None)

    //when(draftApiClient.getAgreementCopyright(1)).thenReturn(Some(agreement1Copyright))

    indexService.indexDocument(image1)
    indexService.indexDocument(image2)
    indexService.indexDocument(image3)
    indexService.indexDocument(image4)
    indexService.indexDocument(image5)

    val servletRequest = mock[HttpServletRequest]
    when(servletRequest.getHeader(any[String])).thenReturn("http")
    when(servletRequest.getServerName).thenReturn("localhost")
    when(servletRequest.getServletPath).thenReturn("/image-api/v2/images/")
    ApplicationUrl.set(servletRequest)

    blockUntil(() => searchService.countDocuments() == 5)
  }

  override def afterAll(): Unit = {
    indexService.deleteSearchIndex(Some(ImageApiProperties.SearchIndex))
  }

  test("That getStartAtAndNumResults returns default values for None-input") {
    searchService invokePrivate getStartAtAndNumResults(None, None) should equal((0, DefaultPageSize))
  }

  test("That getStartAtAndNumResults returns SEARCH_MAX_PAGE_SIZE for value greater than SEARCH_MAX_PAGE_SIZE") {
    searchService invokePrivate getStartAtAndNumResults(None, Some(1000)) should equal((0, MaxPageSize))
  }

  test("That getStartAtAndNumResults returns the correct calculated start at for page and page-size with default page-size") {
    val page = 74
    val expectedStartAt = (page - 1) * DefaultPageSize
    searchService invokePrivate getStartAtAndNumResults(Some(page), None) should equal((expectedStartAt, DefaultPageSize))
  }

  test("That getStartAtAndNumResults returns the correct calculated start at for page and page-size") {
    val page = 123
    val pageSize = 43
    val expectedStartAt = (page - 1) * pageSize
    searchService invokePrivate getStartAtAndNumResults(Some(page), Some(pageSize)) should equal((expectedStartAt, pageSize))
  }

  test("That all returns all documents ordered by id ascending") {
    val searchResult = searchService.all(None, None, None, Sort.ByIdAsc, None, None)
    searchResult.totalCount should be(5)
    searchResult.results.size should be(5)
    searchResult.page should be(1)
    searchResult.results.head.id should be("1")
    searchResult.results.last.id should be("5")
  }

  test("That all filtering on minimumsize only returns images larger than minimumsize") {
    val searchResult = searchService.all(Some(500), None, None, Sort.ByIdAsc, None, None)
    searchResult.totalCount should be(2)
    searchResult.results.size should be(2)
    searchResult.results.head.id should be("1")
    searchResult.results.last.id should be("2")
  }

  test("That all filtering on license only returns images with given license") {
    val searchResult = searchService.all(None, Some("publicdomain"), None, Sort.ByIdAsc, None, None)
    searchResult.totalCount should be(1)
    searchResult.results.size should be(1)
    searchResult.results.head.id should be("2")
  }

  test("That paging returns only hits on current page and not more than page-size") {
    val searchResultPage1 = searchService.all(None, None, None, Sort.ByIdAsc, Some(1), Some(2))
    val searchResultPage2 = searchService.all(None, None, None, Sort.ByIdAsc, Some(2), Some(2))
    searchResultPage1.totalCount should be(5)
    searchResultPage1.page should be(1)
    searchResultPage1.pageSize should be(2)
    searchResultPage1.results.size should be(2)
    searchResultPage1.results.head.id should be("1")
    searchResultPage1.results.last.id should be("2")

    searchResultPage2.totalCount should be(5)
    searchResultPage2.page should be(2)
    searchResultPage2.pageSize should be(2)
    searchResultPage2.results.size should be(2)
    searchResultPage2.results.head.id should be("3")
    searchResultPage2.results.last.id should be("4")
  }

  test("That both minimum-size and license filters are applied.") {
    val searchResult = searchService.all(Some(500), Some("publicdomain"), Some(LanguageTag("und")), Sort.ByIdAsc, None,  None)
    searchResult.totalCount should be(1)
    searchResult.results.size should be(1)
    searchResult.results.head.id should be("2")
  }

  test("That search matches title and alttext ordered by relevance") {
    val searchResult = searchService.matchingQuery("bil", None, None, Sort.ByIdAsc, None, None, None)
    searchResult.totalCount should be(2)
    searchResult.results.size should be(2)
    searchResult.results.head.id should be("1")
    searchResult.results.last.id should be("3")
  }

  test("That search matches title") {
    val searchResult = searchService.matchingQuery("Pingvinen", None, Some(nb), Sort.ByIdAsc, None, None, None)
    searchResult.totalCount should be(1)
    searchResult.results.size should be(1)
    searchResult.results.head.id should be("2")
  }

  test("That search on author matches corresponding author on image") {
    val searchResult = searchService.matchingQuery("Bruce Wayne", None, None, Sort.ByIdAsc, None, None, None)
    searchResult.totalCount should be(1)
    searchResult.results.size should be(1)
    searchResult.results.head.id should be("2")
  }

  test("That search matches tags") {
    val searchResult = searchService.matchingQuery("and", None, Some(nb), Sort.ByIdAsc, None, None, None)
    searchResult.totalCount should be(1)
    searchResult.results.size should be(1)
    searchResult.results.head.id should be("3")
  }

  test("That search defaults to nb if no language is specified") {
    val searchResult = searchService.matchingQuery("Bilde av en and", None, None, Sort.ByIdAsc, None, None, None)
    searchResult.totalCount should be (4)
    searchResult.results.size should be (4)
    searchResult.results.head.id should be ("1")
    searchResult.results(1).id should be ("2")
    searchResult.results(2).id should be ("3")
    searchResult.results.last.id should be ("5")
  }

  test("That search matches title with unknown language analyzed in Norwegian") {
    val searchResult = searchService.matchingQuery("blomst", None, None, Sort.ByIdAsc, None, None, None)
    searchResult.totalCount should be (1)
    searchResult.results.size should be (1)
    searchResult.results.head.id should be ("4")
  }

  test("Searching with logical AND only returns results with all terms") {
    val search1 = searchService.matchingQuery("batmen AND bil", None, Some(nb), Sort.ByIdAsc, None, Some(1), Some(10))
    search1.results.map(_.id) should equal (Seq("1", "3"))

    val search2 = searchService.matchingQuery("batmen | pingvinen", None, Some(nb), Sort.ByIdAsc, None, Some(1), Some(10))
    search2.results.map(_.id) should equal (Seq("1", "2"))

    val search3 = searchService.matchingQuery("bilde + -flaggermusmann", None, Some(nb), Sort.ByIdAsc, None, Some(1), Some(10))
    search3.results.map(_.id) should equal (Seq("2", "3"))

    val search4 = searchService.matchingQuery("batmen + bil", None, Some(nb), Sort.ByIdAsc, None, Some(1), Some(10))
    search4.results.map(_.id) should equal (Seq("1"))
  }

  test("Agreement information should be used in search") {
    val searchResult = searchService.matchingQuery("urelatert", None, None, Sort.ByIdAsc, None, None, None)
    searchResult.totalCount should be (1)
    searchResult.results.size should be (1)
    searchResult.results.head.id should be ("5")
    searchResult.results.head.license should equal(agreement1Copyright.license.license)
  }

  test("Searching for multiple languages should returned matched language") {
    val searchResult1 = searchService.matchingQuery("urelatert", None, Some(LanguageTag("und")), Sort.ByIdAsc, None, None, None)
    searchResult1.totalCount should be (1)
    searchResult1.results.size should be (1)
    searchResult1.results.head.id should be ("5")
    searchResult1.results.head.title.language should equal("unknown")
    searchResult1.results.head.altText.language should equal("unknown")

    val searchResult2 = searchService.matchingQuery("unrelated", None, Some(LanguageTag("und")), Sort.ByTitleDesc, None, None, None)
    searchResult2.totalCount should be (1)
    searchResult2.results.size should be (1)
    searchResult2.results.head.id should be ("5")
    searchResult2.results.head.title.language should equal("en")
    searchResult2.results.head.altText.language should equal("unknown")
  }

  test("That field should be returned in another language if match does not contain searchLanguage") {
    val searchResult = searchService.matchingQuery("unrelated", None, Some(LanguageTag("en")), Sort.ByIdAsc, None,  None, None)
    searchResult.totalCount should be (1)
    searchResult.results.size should be (1)
    searchResult.results.head.id should be ("5")
    searchResult.results.head.title.language should equal("en")
    searchResult.results.head.altText.language should equal("unknown")

    val searchResult2 = searchService.matchingQuery("nynoreg", None, Some(LanguageTag("nn")), Sort.ByIdAsc, None, None, None)
    searchResult2.totalCount should be (1)
    searchResult2.results.size should be (1)
    searchResult2.results.head.id should be ("5")
    searchResult2.results.head.title.language should equal("nn")
    searchResult2.results.head.altText.language should equal("nn")
  }

  test("That supportedLanguages returns in order") {
    val result = searchService.matchingQuery("nynoreg", None, Some(LanguageTag("nn")), Sort.ByIdAsc, None, None, None)
    result.totalCount should be (1)
    result.results.size should be (1)

    result.results.head.supportedLanguages should be(Seq("unknown", "nn", "en"))
  }

  def blockUntil(predicate: () => Boolean): Unit = {
    var backoff = 0
    var done = false

    while (backoff <= 16 && !done) {
      if (backoff > 0) Thread.sleep(200 * backoff)
      backoff = backoff + 1
      try {
        done = predicate()
      } catch {
        case e: Throwable => println("problem while testing predicate", e)
      }
    }

    require(done, s"Failed waiting for predicate")
  }
}
