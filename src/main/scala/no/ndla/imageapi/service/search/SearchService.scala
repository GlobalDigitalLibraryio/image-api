/*
 * Part of NDLA image_api.
 * Copyright (C) 2016 NDLA
 *
 * See LICENSE
 */

package no.ndla.imageapi.service.search

import com.sksamuel.elastic4s.http.ElasticDsl._
import com.sksamuel.elastic4s.http.search.SearchHits
import com.sksamuel.elastic4s.searches.ScoreMode
import com.sksamuel.elastic4s.searches.queries._
import com.sksamuel.elastic4s.searches.queries.term.TermQueryDefinition
import com.sksamuel.elastic4s.searches.sort.FieldSortDefinition
import com.sksamuel.elastic4s.{IndexAndTypes, Indexes}
import com.typesafe.scalalogging.LazyLogging
import io.digitallibrary.language.model.LanguageTag
import no.ndla.imageapi.ImageApiProperties
import no.ndla.imageapi.integration.ElasticClient
import no.ndla.imageapi.model.api.{Error, ImageMetaSummary, SearchResult}
import no.ndla.imageapi.model.search.{SearchableImage, SearchableLanguageFormats}
import no.ndla.imageapi.model.{GdlSearchException, Language, ResultWindowTooLargeException}
import org.elasticsearch.ElasticsearchException
import org.elasticsearch.index.IndexNotFoundException
import org.json4s.Formats
import org.json4s.native.Serialization.read

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

trait SearchService {
  this: ElasticClient with IndexBuilderService with IndexService with SearchConverterService =>
  val searchService: SearchService

  class SearchService extends LazyLogging {
    private val noCopyright = BoolQueryDefinition().not(TermQueryDefinition("license","copyrighted"))

    def createEmptyIndexIfNoIndexesExist(): Unit = {
      val noIndexesExist = indexService.findAllIndexes().map(_.isEmpty).getOrElse(true)
      if (noIndexesExist) {
        indexBuilderService.createEmptyIndex match {
          case Success(_) =>
            logger.info("Created empty index")
            scheduleIndexDocuments()
          case Failure(f) =>
            logger.error(s"Failed to create empty index: $f")
        }
      } else {
        logger.info("Existing index(es) kept intact")
      }
    }

    def getHits(hits: SearchHits, language: Option[LanguageTag]): Seq[ImageMetaSummary] = {
      hits.hits.iterator.toSeq.map(hit => hitAsImageMetaSummary(hit.sourceAsString, language))
    }

    def hitAsImageMetaSummary(hit: String, language: Option[LanguageTag]): ImageMetaSummary = {
      implicit val formats: Formats = SearchableLanguageFormats.JSonFormats
      searchConverterService.asImageMetaSummary(read[SearchableImage](hit), language)
    }

    private def languageSpecificSearch(searchField: String, language: Option[LanguageTag], query: String, boost: Double): QueryDefinition = {
      language.map(_.toString) match {
        case Some(lang) =>
          val searchQuery = SimpleStringQueryDefinition(query).field(s"$searchField.$lang")
          NestedQueryDefinition(searchField, searchQuery, Some(ScoreMode.Avg)).boost(boost)
        case None =>
          Language.supportedLanguages.foldLeft(BoolQueryDefinition())((result, lang) => {
            val searchQuery = SimpleStringQueryDefinition(query).field(s"$searchField.$lang")
            result.should(NestedQueryDefinition(searchField, searchQuery, Some(ScoreMode.Avg)).boost(boost))
          })
      }
    }

    def matchingQuery(query: String, minimumSize: Option[Int], language: Option[LanguageTag], license: Option[String], page: Option[Int], pageSize: Option[Int]): SearchResult = {
      val fullSearch = BoolQueryDefinition()
        .must(BoolQueryDefinition()
          .should(languageSpecificSearch("titles", language, query, 2))
          .should(languageSpecificSearch("alttexts", language, query, 1))
          .should(languageSpecificSearch("captions", language, query, 2))
          .should(languageSpecificSearch("tags", language, query, 2)))

      executeSearch(fullSearch, minimumSize, license, language, page, pageSize)
    }

    def all(minimumSize: Option[Int], license: Option[String], language: Option[LanguageTag], page: Option[Int], pageSize: Option[Int]): SearchResult =
      executeSearch(BoolQueryDefinition(), minimumSize, license, language, page, pageSize)

    def executeSearch(queryDefinition: BoolQueryDefinition, minimumSize: Option[Int], license: Option[String], language: Option[LanguageTag], page: Option[Int], pageSize: Option[Int]): SearchResult = {
      val licensedFiltered = license match {
        case None => queryDefinition.filter(noCopyright)
        case Some(lic) => queryDefinition.filter(TermQueryDefinition("license", lic))
      }

      val sizeFiltered = minimumSize match {
        case None => licensedFiltered
        case Some(_) => licensedFiltered.filter(RangeQueryDefinition("imageSize").gte(minimumSize.get))
      }

      val languageFiltered = language match {
        case None => sizeFiltered
        case Some(lang) => sizeFiltered.filter(NestedQueryDefinition("titles", ExistsQueryDefinition(s"titles.$lang"), Some(ScoreMode.Avg)))
      }

      val (startAt, numResults) = getStartAtAndNumResults(page, pageSize)
      val requestedResultWindow = page.getOrElse(1)*numResults

      if(requestedResultWindow > ImageApiProperties.ElasticSearchIndexMaxResultWindow) {
        logger.info(s"Max supported results are ${ImageApiProperties.ElasticSearchIndexMaxResultWindow}, user requested $requestedResultWindow")
        throw new ResultWindowTooLargeException(Error.WindowTooLargeError.description)
      }
      val indexAndTypes = IndexAndTypes(ImageApiProperties.SearchIndex, ImageApiProperties.SearchDocument)

      val search = searchWithType(indexAndTypes)
        .bool(languageFiltered)
        .size(numResults)
        .from(startAt)
        .sortBy(FieldSortDefinition("id"))

      esClient.execute(
        search
      ) match {
        case Success(response) => SearchResult(response.result.totalHits, page.getOrElse(1), numResults, getHits(response.result.hits, language))
        case Failure(failure) => errorHandler(Failure(failure))
      }
    }

    def countDocuments(): Long = {
      val ret = esClient.execute(
        count(Indexes(ImageApiProperties.SearchIndex))
      ).map(result => result.result.count)
      ret.getOrElse(0)
    }

    private def getStartAtAndNumResults(page: Option[Int], pageSize: Option[Int]): (Int, Int) = {
      val numResults = pageSize match {
        case Some(num) =>
          if (num > 0) num.min(ImageApiProperties.MaxPageSize) else ImageApiProperties.DefaultPageSize
        case None => ImageApiProperties.DefaultPageSize
      }

      val startAt = page match {
        case Some(sa) => (sa - 1).max(0) * numResults
        case None => 0
      }

      (startAt, numResults)
    }

    private def errorHandler[T](failure: Failure[T]) = {
      failure match {
        case Failure(e: GdlSearchException) =>
          e.getFailure.status match {
            case 404 =>
              logger.error(s"Index ${ImageApiProperties.SearchIndex} not found. Scheduling a reindex.")
              scheduleIndexDocuments()
              throw new IndexNotFoundException(s"Index ${ImageApiProperties.SearchIndex} not found. Scheduling a reindex")
            case _ =>
              logger.error(e.getFailure.error.reason)
              throw new ElasticsearchException(s"Unable to execute search in ${ImageApiProperties.SearchIndex}", e.getFailure.error.reason)
          }
        case Failure(t: Throwable) => throw t
      }
    }

    private def scheduleIndexDocuments(): Unit = {
      val f = Future {
        indexBuilderService.indexDocuments
      }

      f.failed.foreach(t => logger.warn("Unable to create index: " + t.getMessage, t))
      f.foreach {
        case Success(reindexResult) => logger.info(s"Completed indexing of ${reindexResult.totalIndexed} documents in ${reindexResult.millisUsed} ms.")
        case Failure(ex) => logger.warn(ex.getMessage, ex)
      }
    }
  }
}