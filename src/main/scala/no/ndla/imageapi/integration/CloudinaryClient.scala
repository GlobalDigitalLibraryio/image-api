package no.ndla.imageapi.integration

import java.io.{File, InputStream, InputStreamReader}
import java.nio.file.{Files, Path}

import com.cloudinary.Cloudinary
import com.cloudinary.utils.ObjectUtils
import com.sun.org.apache.xml.internal.resolver.helpers.PublicId
import com.typesafe.scalalogging.LazyLogging
import no.ndla.imageapi.ImageApiProperties
import no.ndla.imageapi.model.domain.CloudinaryInfo

import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

trait CloudinaryClient {
  val cloudinaryClient: CloudinaryClient

  class CloudinaryClient(cloudName: String, apiKey: String, apiSecret: String) extends LazyLogging {


    val client = new Cloudinary(ObjectUtils.asMap("cloud_name", cloudName, "api_key", apiKey, "api_secret", apiSecret))

    def uploadFromStream(inputStream: InputStream, storageKey: String): Map[String, String] = {
      val tempFile = Files.createTempFile("img-temp", ".temp")
      val buffer = new Array[Byte](inputStream.available)
      val read = inputStream.read(buffer)
      Files.write(tempFile, buffer)
      val returnMap = uploadFromFile(tempFile.toFile, storageKey)
      Files.deleteIfExists(tempFile)

      returnMap
    }

    def uploadFromFile(file: File, publicId: String): Map[String, String] = {
      val options = ObjectUtils.asMap("public_id", publicId)
      val upload = client.uploader.upload(file, options)
      upload.asScala.map(x => (x._1.toString, x._2.toString)).toSet.toMap
    }

    def doesObjectExist(publicId: String): Boolean = {
      Try(client.uploader().explicit(publicId, ObjectUtils.asMap("type", "upload"))) match {
        case Success(_) => true
        case Failure(_) => false
      }
    }

    def deleteObject(publicId: String): Try[_] = {
      Try(client.uploader.destroy(publicId, ObjectUtils.emptyMap()))
    }

    def getInformation(publicId: String): Try[CloudinaryInfo] = {
      Try(client.uploader().explicit(publicId, ObjectUtils.asMap("type", "upload"))).map(response => {
        val values = response.asScala.map(x => (x._1.toString, x._2.toString)).toSet.toMap
        CloudinaryInfo(
          publicId = values("public_id"),
          format = values("format"),
          resource_type = values("resource_type"),
          height = values("height").toLong,
          width = values("width").toLong,
          bytes = values("bytes").toLong,
          url = values("secure_url")
        )
      })
    }
  }
}
