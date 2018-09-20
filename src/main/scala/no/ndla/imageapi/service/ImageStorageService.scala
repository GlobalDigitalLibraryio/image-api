/*
 * Part of NDLA image_api.
 * Copyright (C) 2016 NDLA
 *
 * See LICENSE
 *
 */

package no.ndla.imageapi.service

import java.awt.image.BufferedImage
import java.io.InputStream
import java.net.URL

import com.typesafe.scalalogging.LazyLogging
import javax.imageio.ImageIO
import no.ndla.imageapi.integration.{AmazonClient, CloudinaryClient}
import no.ndla.imageapi.model.domain.{CloudinaryInfo, Image, ImageStream, MediaType}
import scalaj.http.HttpRequest

import scala.util.Try

trait ImageStorageService {
  this: AmazonClient with CloudinaryClient =>
  val imageStorage: AmazonImageStorageService

  class AmazonImageStorageService extends LazyLogging {

    case class CloudinaryImage(cloudinaryImage: CloudinaryInfo, storageKey: String) extends ImageStream {
      override def contentType: String = MediaType.fromFileExtension(cloudinaryImage.format).toString
      override def stream: InputStream = new URL(cloudinaryImage.url).openStream()
      override def fileName: String = cloudinaryImage.publicId
      override val sourceImage: BufferedImage = ImageIO.read(new URL(cloudinaryImage.url))
    }

    def get(imageKey: String): Try[ImageStream] = {
      cloudinaryClient.getInformation(imageKey).map(info => CloudinaryImage(info, imageKey))
    }

    def uploadFromUrl(image: Image, storageKey: String, request: HttpRequest): Try[String] =
      request.execute(stream => uploadFromStream(stream, storageKey, image.contentType, image.size)).body

    def uploadFromStream(stream: InputStream, storageKey: String, contentType: String, size: Long): Try[String] = {
      Try(cloudinaryClient.uploadFromStream(stream, storageKey).find(_._1 == "public_id").get._2)
    }

    def objectExists(storageKey: String): Boolean = cloudinaryClient.doesObjectExist(storageKey)

    def deleteObject(storageKey: String): Try[_] = cloudinaryClient.deleteObject(storageKey)

  }

}
