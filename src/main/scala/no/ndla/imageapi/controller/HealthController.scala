/*
 * Part of NDLA image_api.
 * Copyright (C) 2016 NDLA
 *
 * See LICENSE
 *
 */

package no.ndla.imageapi.controller

import com.netaporter.uri.Uri.parse
import com.netaporter.uri.config.UriConfig
import com.netaporter.uri.dsl._
import com.typesafe.scalalogging.LazyLogging
import io.digitallibrary.network.ApplicationUrl
import no.ndla.imageapi.ImageApiProperties
import no.ndla.imageapi.repository.ImageRepository
import org.scalatra._
import scalaj.http.{Http, HttpResponse}


trait HealthController {
  this: ImageRepository =>
  val healthController: HealthController

  class HealthController extends ScalatraServlet with LazyLogging {

    before() {
      ApplicationUrl.set(request)
    }

    after() {
      ApplicationUrl.clear
    }

    def getApiResponse(url: String): HttpResponse[String] = {
      Http(url).execute()
    }

    def getReturnCode(imageResponse: HttpResponse[String]): ActionResult = {
      imageResponse.code match {
        case 200 => Ok()
        case _ => InternalServerError()
      }
    }

    get("/") {
      val applicationUrl = ApplicationUrl.get
      val host = applicationUrl.host.getOrElse("0")
      val port = applicationUrl.port.getOrElse("80")

      imageRepository.getRandomImage().map(image => {
        val previewUrl = s"http://$host:$port${ImageApiProperties.RawControllerPath}${parse(image.imageUrl)(UriConfig()).toString}"
        getReturnCode(getApiResponse(previewUrl))
      }).getOrElse(Ok())
    }
  }

}
