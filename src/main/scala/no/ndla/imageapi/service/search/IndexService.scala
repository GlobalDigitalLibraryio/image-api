/*
 * Part of NDLA image_api.
 * Copyright (C) 2016 NDLA
 *
 * See LICENSE
 */

package no.ndla.imageapi.service.search

import java.text.SimpleDateFormat
import java.util.{Calendar, UUID}

import com.sksamuel.elastic4s.alias.{AddAliasActionDefinition, AliasActionDefinition, RemoveAliasActionDefinition}
import com.sksamuel.elastic4s.http.ElasticDsl.{createIndex, _}
import com.sksamuel.elastic4s.indexes.IndexDefinition
import com.sksamuel.elastic4s.mappings.{MappingDefinition, NestedFieldDefinition}
import com.sksamuel.elastic4s.{IndexAndType, RefreshPolicy}
import com.typesafe.scalalogging.LazyLogging
import no.ndla.imageapi.ImageApiProperties
import no.ndla.imageapi.integration.ElasticClient
import no.ndla.imageapi.model.Language._
import no.ndla.imageapi.model.domain
import no.ndla.imageapi.model.search.SearchableLanguageFormats
import org.json4s.native.Serialization.write

import scala.collection.immutable
import scala.util.{Failure, Success, Try}

trait IndexService {
  this: ElasticClient with SearchConverterService =>
  val indexService: IndexService

  class IndexService extends LazyLogging {
    implicit val formats = SearchableLanguageFormats.JSonFormats

    def indexDocument(imageMetaInformation: domain.ImageMetaInformation): Try[domain.ImageMetaInformation] = {
      val source = write(searchConverterService.asSearchableImage(imageMetaInformation))
      esClient.execute(
        indexInto(ImageApiProperties.SearchIndex, ImageApiProperties.SearchDocument)
          .id(imageMetaInformation.id.toString)
          .source(source)
      ) match {
        case Success(_) => Success(imageMetaInformation)
        case Failure(failure) => Failure(failure)
      }
    }

    def indexDocuments(imageMetaList: List[domain.ImageMetaInformation], indexName: String): Try[Int] = {
      val actions: immutable.Seq[IndexDefinition] = for {imageMeta <- imageMetaList
       source = write(searchConverterService.asSearchableImage(imageMeta))
       indexAndType = new IndexAndType(indexName, ImageApiProperties.SearchDocument)
      } yield IndexDefinition(indexAndType, id = Some(imageMeta.id.get.toString), source = Some(source))

      esClient.execute(
        bulk(actions).refresh(RefreshPolicy.WAIT_UNTIL)
      ) match {
        case Failure(failure) => Failure(failure)
        case Success(_) =>
          logger.info(s"Indexed ${imageMetaList.size} documents")
          Success(imageMetaList.size)
      }
    }

    def createSearchIndex(): Try[String] = {
      createIndexWithName(ImageApiProperties.SearchIndex + "_" + getTimestamp + "_" + UUID.randomUUID().toString)
    }

    def createIndexWithName(indexName: String): Try[String] = {
      if (indexExisting(indexName).getOrElse(false)) {
        Success(indexName)
      } else {
        esClient.execute(
          createIndex(indexName)
            .indexSetting("max_result_window",ImageApiProperties.ElasticSearchIndexMaxResultWindow)
            .mappings(mappings())
        ) match {
          case Failure(failure) =>
            logger.error(failure.getMessage)
            Failure(failure)
          case Success(_) => Success(indexName)
        }
      }
    }

    private def mappings(): List[MappingDefinition] = {
      List(
        mapping(ImageApiProperties.SearchDocument) as (
          intField("id"),
          keywordField("license"),
          intField("imageSize"),
          keywordField("previewUrl"),
          languageSupportedField("titles", keepRaw = true),
          languageSupportedField("alttexts", keepRaw = false),
          languageSupportedField("captions", keepRaw = false),
          languageSupportedField("tags", keepRaw = false)
        )
      )
    }

    def findAllIndexes(): Try[Seq[String]] = {
      esClient.execute(
        getAliases()
      ) match {
        case Failure(failure) => Failure(failure)
        case Success(response) => Success(response.result.mappings.keys.toSeq.map(_.name).filter(name => name.startsWith(ImageApiProperties.SearchIndex)))
      }
    }

    def updateAliasTarget(oldIndexName: Option[String], newIndexName: String): Try[Any] = {
      if (!indexExisting(newIndexName).getOrElse(false)) {
        Failure(new IllegalArgumentException(s"No such index: $newIndexName"))
      } else {
        var actions = List[AliasActionDefinition](AddAliasActionDefinition(ImageApiProperties.SearchIndex, newIndexName))
        oldIndexName match {
          case None => // Do nothing
          case Some(oldIndex) =>
            actions = actions :+ RemoveAliasActionDefinition(ImageApiProperties.SearchIndex, oldIndex)
        }
        esClient.execute(
          aliases(actions)
        ) match {
          case Failure(failure) => Failure(failure)
          case Success(_) => Success()
        }
      }
    }

    def deleteSearchIndex(optIndexName: Option[String]): Try[_] = {
      optIndexName match {
        case None => Success(optIndexName)
        case Some(indexName) =>
          if (!indexExisting(indexName).getOrElse(false)) {
            Failure(new IllegalArgumentException(s"No such index: $indexName"))
          } else {
            esClient.execute(
              deleteIndex(indexName)
            ) match {
              case Failure(failure) => Failure(failure)
              case Success(_) => Success()
            }
          }
      }
    }

    def aliasTarget: Try[Option[String]] = {
      esClient.execute(
        getAliases(ImageApiProperties.SearchIndex, Nil)
      ) match {
        case Failure(failure) => Failure(failure)
        case Success(response) =>
          response.result.mappings.headOption match {
            case Some((index, _)) => Success(Some(index.name))
            case None => Success(None)
          }
      }
    }

    def indexExisting(indexName: String): Try[Boolean] = {
      esClient.execute(indexExists(indexName)) match {
        case Success(response) => Success(response.result.isExists)
        case Failure(_) => Success(false)
      }
    }

    private def languageSupportedField(fieldName: String, keepRaw: Boolean = false) = {
      val languageSupportedField = NestedFieldDefinition(fieldName)
      languageSupportedField.fields( keepRaw match {
        case true => languageAnalyzers.map(langAnalyzer => textField(langAnalyzer.lang).fielddata(true) analyzer langAnalyzer.analyzer fields (keywordField("raw") index false))
        case false => languageAnalyzers.map(langAnalyzer => textField(langAnalyzer.lang).fielddata(true) analyzer langAnalyzer.analyzer)
      })

      languageSupportedField
    }

    private def getTimestamp: String = {
      new SimpleDateFormat("yyyyMMddHHmmss").format(Calendar.getInstance.getTime)
    }
  }

}
