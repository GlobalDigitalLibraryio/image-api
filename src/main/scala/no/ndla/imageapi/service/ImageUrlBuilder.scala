package no.ndla.imageapi.service

import no.ndla.imageapi.ImageApiProperties
import no.ndla.imageapi.model.domain.{ImageMetaInformation, StorageService}

object ImageUrlBuilder {

  def urlFor(metadata: ImageMetaInformation, width: Option[Int] = None, height: Option[Int] = None): String = {
    urlForFile(metadata.imageUrl, metadata.storageService.getOrElse(StorageService.AWS), width, height)
  }

  def urlForFile(filename: String, service: StorageService.Value, width: Option[Int] = None, height: Option[Int] = None): String = {
    service match {
      case StorageService.AWS => urlToAws(filename, width, height)
      case StorageService.CLOUDINARY => urlToCloudinary(filename, width, height)
    }
  }

  private def urlToCloudinary(filename: String, width: Option[Int], height: Option[Int]): String = {
    ImageApiProperties.CloudinaryUrl +
      width.map(w => s",w_$w").getOrElse("") +
      height.map(h => s",h_$h").getOrElse("") +
      (if (filename.startsWith("/")) filename else "/" + filename)
  }

  private def urlToAws(filename: String, width: Option[Int], height: Option[Int]): String = {
    val parameters = Seq(
      width.map(w => s"width=$w"),
      height.map(h => s"height=$h")
    ).flatten.mkString("&")

    ImageApiProperties.CloudFrontUrl +
      (if (filename.startsWith("/")) filename else "/" + filename) + (if (!parameters.isEmpty) s"?$parameters" else "")
  }
}




