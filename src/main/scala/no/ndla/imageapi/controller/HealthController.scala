/*
 * Part of NDLA image_api.
 * Copyright (C) 2016 NDLA
 *
 * See LICENSE
 *
 */

package no.ndla.imageapi.controller

import com.typesafe.scalalogging.LazyLogging
import no.ndla.imageapi.ImageApiProperties
import org.scalatra._

import scala.util.{Failure, Success, Try}
import scalaj.http.Http


trait HealthController {
  val healthController: HealthController

  class HealthController extends ScalatraServlet with LazyLogging {

    def checker = new CheckThatEndpointResponds

    val endpointsToCheck = Seq(
      s"http://0.0.0.0:${ImageApiProperties.ApplicationPort}${ImageApiProperties.ImageApiBasePath}/v1/images/",
    )

    get("/") {
      val allEndpointsResponds = endpointsToCheck.toStream.map(checker.responds(_)).takeWhile(_ == true).length == endpointsToCheck.length
      if (allEndpointsResponds) {
        Ok()
      } else {
        InternalServerError()
      }
    }
  }

}

class CheckThatEndpointResponds extends LazyLogging {
  def responds(url: String): Boolean = {
    Try(Http(url).execute()) match {
      case Success(_) => true
      case Failure(_) => false
    }
  }
}
