package no.ndla.imageapi.service

import java.io.ByteArrayInputStream

import com.typesafe.scalalogging.LazyLogging
import no.ndla.imageapi.model.ValidationException
import no.ndla.imageapi.model.api.NewImageMetaInformation
import no.ndla.imageapi.model.domain.{Image, ImageMetaInformation}
import no.ndla.imageapi.repository.ImageRepository
import no.ndla.imageapi.service.search.IndexService
import org.scalatra.servlet.FileItem

import scala.util.{Failure, Success, Try}

trait WriteService {
  this: ConverterService with ValidationService with ImageRepository with IndexService with ImageStorageService =>
  val writeService: WriteService

  class WriteService extends LazyLogging {
    def storeNewImage(newImage: NewImageMetaInformation, file: FileItem): Try[ImageMetaInformation] = {
      validationService.validateImageFile(file) match {
        case Some(validationMessage) => return Failure(new ValidationException(errors = Seq(validationMessage)))
        case _ =>
      }

      val domainImage = uploadImage(file).map(uploadedImage =>
        converterService.asDomainImageMetaInformation(newImage, uploadedImage)) match {
        case Failure(e) => return Failure(e)
        case Success(image) => image
      }

      validationService.validate(domainImage) match {
        case Failure(e) =>
          imageStorage.deleteObject(domainImage.imageUrl)
          return Failure(e)
        case _ =>
      }

      val imageMeta = Try(imageRepository.insert(domainImage, newImage.externalId)) match {
        case Success(meta) => meta
        case Failure(e) =>
          imageStorage.deleteObject(domainImage.imageUrl)
          return Failure(e)
      }

      indexService.indexDocument(imageMeta) match {
        case Success(_) => Success(imageMeta)
        case Failure(e) =>
          imageStorage.deleteObject(domainImage.imageUrl)
          imageRepository.delete(imageMeta.id.get)
          Failure(e)
      }
    }

    private[service] def uploadImage(file: FileItem): Try[Image] = {
      val contentType = file.getContentType.getOrElse("")
      val fileName = filenameToHashFilename(file.name, md5Hash(file.get))
      if (imageStorage.objectExists(fileName)) {
        logger.info(s"$fileName already exists in S3, skipping upload and using existing image")
        Success(Image(fileName, file.size, contentType))
      } else {
        imageStorage.uploadFromStream(new ByteArrayInputStream(file.get), fileName, contentType, file.size).map(filePath => {
          Image(filePath, file.size, contentType)
        })
      }
    }

    def filenameToHashFilename(filename: String, hash: String): String = {
      val extension = filename.lastIndexOf(".") match {
        case index: Int if index > -1 => filename.substring(index + 1)
        case _ => ""
      }
      s"$hash.$extension"
    }

    def md5Hash(bytes: Array[Byte]): String =
      java.security.MessageDigest.getInstance("MD5").digest(bytes).map(0xFF & _).map {
        "%02x".format(_)
      }.foldLeft("") {
        _ + _
      }

  }

}
