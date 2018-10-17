/*
 * Part of NDLA image_api.
 * Copyright (C) 2016 NDLA
 *
 * See LICENSE
 *
 */

package no.ndla.imageapi.controller

import com.typesafe.scalalogging.LazyLogging
import no.ndla.imageapi.repository.ImageRepository
import org.scalatra._

import scala.util.{Failure, Success, Try}


trait HealthController {
  this: ImageRepository =>
  val healthController: HealthController

  class HealthController extends ScalatraServlet with LazyLogging {
    get("/") {
      Try(imageRepository.getRandomImage()) match {
        case Success(Some(_)) => Ok()
        case Success(None) => Ok()
        case Failure(ex) => {
          logger.error("HealthController could not query database", ex)
          InternalServerError()
        }
      }
    }
  }

}
