/*
 * Part of NDLA image_api.
 * Copyright (C) 2016 NDLA
 *
 * See LICENSE
 *
 */

package no.ndla.imageapi.service

import java.awt.image.BufferedImage
import java.io.{ByteArrayInputStream, ByteArrayOutputStream, InputStream}
import java.net.URL

import com.amazonaws.services.s3.model.{GetObjectRequest, S3Object}
import com.typesafe.scalalogging.LazyLogging
import javax.imageio.ImageIO
import no.ndla.imageapi.ImageApiProperties.StorageName
import no.ndla.imageapi.integration.{AmazonClient, CloudinaryClient}
import no.ndla.imageapi.model.ImageNotFoundException
import no.ndla.imageapi.model.domain.{CloudinaryInfo, Image, ImageStream, MediaType}
import scalaj.http.HttpRequest

import scala.util.{Failure, Success, Try}

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

    case class NdlaImage(s3Object: S3Object, fileName: String) extends ImageStream {
      override val sourceImage: BufferedImage = {
        val stream = s3Object.getObjectContent
        val content = ImageIO.read(stream)
        stream.close()
        content
      }

      override def contentType: String = s3Object.getObjectMetadata.getContentType

      override def stream: InputStream = {
        val outputStream = new ByteArrayOutputStream()
        ImageIO.write(sourceImage, format, outputStream)
        new ByteArrayInputStream(outputStream.toByteArray)
      }
    }

    def get(imageKey: String): Try[ImageStream] = {
      Try(amazonClient.getObject(new GetObjectRequest(StorageName, imageKey))).map(s3Object => NdlaImage(s3Object, imageKey)) match {
        case Success(e) => Success(e)
        case Failure(e) => Failure(new ImageNotFoundException(s"Image $imageKey does not exist"))
      }
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
