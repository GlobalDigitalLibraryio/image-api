/*
 * Part of NDLA image_api.
 * Copyright (C) 2016 NDLA
 *
 * See LICENSE
 *
 */

package no.ndla.imageapi.controller

import io.digitallibrary.language.model.LanguageTag
import no.ndla.imageapi.model.Language
import no.ndla.imageapi.model.api.Error
import no.ndla.imageapi.repository.ImageRepository
import no.ndla.imageapi.service.ConverterService
import no.ndla.imageapi.service.search.{IndexBuilderService, IndexService}
import org.json4s.{DefaultFormats, Formats}
import org.scalatra.{InternalServerError, NotFound, Ok}

import scala.util.{Failure, Success}

trait InternController {
  this: ImageRepository with ConverterService with IndexBuilderService with IndexService =>
  val internController: InternController

  class InternController extends NdlaController {

    protected implicit override val jsonFormats: Formats = DefaultFormats + new LanguageTagSerializer

    post("/index") {
      indexBuilderService.indexDocuments match {
        case Success(reindexResult) => {
          val result = s"Completed indexing of ${reindexResult.totalIndexed} documents in ${reindexResult.millisUsed} ms."
          logger.info(result)
          Ok(result)
        }
        case Failure(f) => {
          logger.warn(f.getMessage, f)
          InternalServerError(f.getMessage)
        }
      }
    }

    delete("/index") {
      def pluralIndex(n: Int) = if (n == 1) "1 index" else s"$n indexes"
      val deleteResults = indexService.findAllIndexes() match {
        case Failure(f) => halt(status = 500, body = f.getMessage)
        case Success(indexes) => indexes.map(index => {
          logger.info(s"Deleting index $index")
          indexService.deleteSearchIndex(Option(index))
        })
      }
      val (errors, successes) = deleteResults.partition(_.isFailure)
      if (errors.nonEmpty) {
        val message = s"Failed to delete ${pluralIndex(errors.length)}: " +
          s"${errors.map(_.failed.get.getMessage).mkString(", ")}. " +
          s"${pluralIndex(successes.length)} were deleted successfully."
        halt(status = 500, body = message)
      } else {
        Ok(body = s"Deleted ${pluralIndex(successes.length)}")
      }
    }

    get("/extern/:image_id") {
      val externalId = params("image_id")
      val language = paramOrNone("language").map(LanguageTag(_)).getOrElse(Language.DefaultLanguage)
      imageRepository.withExternalId(externalId) match {
        case Some(image) => Ok(converterService.asApiImageMetaInformationWithDomainUrlAndSingleLanguage(image, language))
        case None => NotFound(Error(Error.NOT_FOUND, s"Image with external id $externalId not found"))
      }
    }

  }

}
