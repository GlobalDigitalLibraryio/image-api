/*
 * Part of NDLA Image-API. API for searching and downloading images from NDLA.
 * Copyright (C) 2015 NDLA
 *
 * See LICENSE
 */
package no.ndla.imageapi.service

import java.util

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s._
import com.typesafe.scalalogging.LazyLogging
import no.ndla.imageapi.ImageApiProperties
import no.ndla.imageapi.integration.ElasticClientComponent
import no.ndla.imageapi.model.ImageMetaSummary
import no.ndla.imageapi.network.ApplicationUrl
import no.ndla.imageapi.repository.SearchIndexerComponent
import org.elasticsearch.index.query.MatchQueryBuilder
import org.elasticsearch.indices.IndexMissingException
import org.elasticsearch.transport.RemoteTransportException

import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait SearchService {
  this: ElasticClientComponent with SearchIndexerComponent =>
  val searchService: ElasticContentSearch

  class ElasticContentSearch extends LazyLogging {

    val noCopyrightFilter = not(nestedFilter("copyright.license").filter(termFilter("license", "copyrighted")))

    implicit object ImageHitAs extends HitAs[ImageMetaSummary] {
      override def as(hit: RichSearchHit): ImageMetaSummary = {
        val sourceMap = hit.sourceAsMap
        ImageMetaSummary(
          sourceMap("id").toString,
          ApplicationUrl.get + sourceMap("images").asInstanceOf[util.HashMap[String, AnyRef]].get("small").asInstanceOf[util.HashMap[String, String]].get("url"),
          ApplicationUrl.get + sourceMap("id").toString,
          sourceMap("copyright").asInstanceOf[util.HashMap[String, AnyRef]].get("license").asInstanceOf[util.HashMap[String, String]].get("license"))
      }
    }


    def matchingQuery(query: Iterable[String], minimumSize: Option[Int], language: Option[String], license: Option[String], page: Option[Int], pageSize: Option[Int]): Iterable[ImageMetaSummary] = {
      val titleSearch = new ListBuffer[QueryDefinition]
      titleSearch += matchQuery("titles.title", query.mkString(" ")).operator(MatchQueryBuilder.Operator.AND)
      language.foreach(lang => titleSearch += termQuery("titles.language", lang))

      val altTextSearch = new ListBuffer[QueryDefinition]
      altTextSearch += matchQuery("alttexts.alttext", query.mkString(" ")).operator(MatchQueryBuilder.Operator.AND)
      language.foreach(lang => altTextSearch += termQuery("alttexts.language", lang))

      val tagSearch = new ListBuffer[QueryDefinition]
      tagSearch += matchQuery("tags.tag", query.mkString(" ")).operator(MatchQueryBuilder.Operator.AND)
      language.foreach(lang => tagSearch += termQuery("tags.language", lang))

      val theSearch = search in ImageApiProperties.SearchIndex -> ImageApiProperties.SearchDocument query {
        bool {
          should(
            nestedQuery("titles").query {
              bool {
                must(titleSearch.toList)
              }
            },
            nestedQuery("alttexts").query {
              bool {
                must(altTextSearch.toList)
              }
            },
            nestedQuery("tags").query {
              bool {
                must(tagSearch.toList)
              }
            }
          )
        }
      }

      val (startAt, numResults) = getStartAtAndNumResults(page, pageSize)

      val filterList = new ListBuffer[FilterDefinition]()
      license.foreach(license => filterList += nestedFilter("copyright.license").filter(termFilter("license", license)))
      minimumSize.foreach(size => filterList += nestedFilter("images.full").filter(rangeFilter("images.full.size").gte(size.toString)))
      filterList += noCopyrightFilter

      if (filterList.nonEmpty) {
        theSearch.postFilter(must(filterList.toList))
      }

      executeSearch(theSearch, page, pageSize)
    }

    def all(minimumSize: Option[Int], license: Option[String], page: Option[Int], pageSize: Option[Int]): Iterable[ImageMetaSummary] = {
      val theSearch = search in ImageApiProperties.SearchIndex -> ImageApiProperties.SearchDocument

      val filterList = new ListBuffer[FilterDefinition]()
      license.foreach(license => filterList += nestedFilter("copyright.license").filter(termFilter("license", license)))
      minimumSize.foreach(size => filterList += nestedFilter("images.full").filter(rangeFilter("images.full.size").gte(size.toString)))
      filterList += noCopyrightFilter

      if (filterList.nonEmpty) {
        theSearch.postFilter(must(filterList.toList))
      }
      theSearch.sort(field sort "id")

      executeSearch(theSearch, page, pageSize)
    }

    private def executeSearch(search: SearchDefinition, page: Option[Int], pageSize: Option[Int]) = {
      val (startAt, numResults) = getStartAtAndNumResults(page, pageSize)
      try {
        elasticClient.execute {
          search start startAt limit numResults
        }.await.as[ImageMetaSummary]
      } catch {
        case e: RemoteTransportException => errorHandler(e.getCause)
      }
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

    private def errorHandler(exception: Throwable) = {
      exception match {
        case ex: IndexMissingException =>
          logger.error(ex.getDetailedMessage)
          scheduleIndexDocuments()
          throw ex
        case _ => throw exception
      }
    }

    private def scheduleIndexDocuments() = {
      val f = Future {
        searchIndexer.indexDocuments()
      }
      f onFailure { case t => logger.error("Unable to create index: " + t.getMessage) }
    }
  }
}