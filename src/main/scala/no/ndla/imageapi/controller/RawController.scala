package no.ndla.imageapi.controller

import javax.servlet.http.HttpServletRequest

import com.netaporter.uri.Uri.{parse => uriParse}
import no.ndla.imageapi.model.api.{Error, RawImageQueryParameters}
import no.ndla.imageapi.model.domain.ImageStream
import no.ndla.imageapi.repository.ImageRepository
import no.ndla.imageapi.service.{ConverterService, ImageConverter, ImageStorageService}
import org.scalatra.Ok
import org.scalatra.swagger.DataType.ValueDataType
import org.scalatra.swagger.SwaggerSupportSyntax.OperationBuilder
import org.scalatra.swagger.{Parameter, ResponseMessage, Swagger, SwaggerSupport}
import org.json4s.native.Serialization.{write, _}

import scala.util.{Failure, Success, Try}

trait RawController {
  this: ImageStorageService with ImageConverter with ImageRepository with ConverterService =>
  val rawController: RawController

  class RawController(implicit val swagger: Swagger) extends NdlaController with SwaggerSupport {
    protected val applicationDescription = "API for accessing image files from ndla.no."

    registerModel[Error]()

    val response404 = ResponseMessage(404, "Not found", Some("Error"))
    val response500 = ResponseMessage(500, "Unknown error", Some("Error"))
    val staticAssetCacheHeaders = Map("Cache-Control" -> "public, max-age=31536000, immutable")

    val getImageParams: List[Parameter] = List(
      headerParam[Option[String]]("X-Correlation-ID").description("User supplied correlation-id. May be omitted."),
      headerParam[Option[String]]("app-key").description("Your app-key. May be omitted to access api anonymously, but rate limiting may apply on anonymous access."),
      queryParam[Option[Int]]("width").description("The target width to resize the image (the unit is pixles). Image proportions are kept intact"),
      queryParam[Option[Int]]("height").description("The target height to resize the image (the unit is pixles). Image proportions are kept intact"),
      queryParam[Option[Int]]("cropStartX").description("The first image coordinate X, in percent (0 to 100), specifying the crop start position. If used the other crop parameters must also be supplied"),
      queryParam[Option[Int]]("cropStartY").description("The first image coordinate Y, in percent (0 to 100), specifying the crop start position. If used the other crop parameters must also be supplied"),
      queryParam[Option[Int]]("cropEndX").description("The end image coordinate X, in percent (0 to 100), specifying the crop end position. If used the other crop parameters must also be supplied"),
      queryParam[Option[Int]]("cropEndY").description("The end image coordinate Y, in percent (0 to 100), specifying the crop end position. If used the other crop parameters must also be supplied"),
      queryParam[Option[Int]]("focalX").description("The end image coordinate X, in percent (0 to 100), specifying the focal point. If used the other focal point parameter, width and/or height, must also be supplied"),
      queryParam[Option[Int]]("focalY").description("The end image coordinate Y, in percent (0 to 100), specifying the focal point. If used the other focal point parameter, width and/or height, must also be supplied"),
      queryParam[Option[Double]]("ratio").description("The wanted aspect ratio, defined as width/height. To be used together with the focal parameters. If used the width and height is ignored and derived from the aspect ratio instead."),
      queryParam[Option[Double]]("storedRatio").description("Use stored crop and focal point parameters for given aspect ratio, or use defaults instead.")
    )

    val getImageFile = new OperationBuilder(ValueDataType("file", Some("binary")))
      .nickname("getImageFile")
      .summary("Fetch an image with options to resize and crop")
      .notes("Fetches a image with options to resize and crop")
      .produces("application/octet-stream")
      .parameters(
        List[Parameter](pathParam[String]("name").description("The name of the image"))
          ++ getImageParams:_*
      ).responseMessages(response404, response500)

    val getImageFileById = new OperationBuilder(ValueDataType("file", Some("binary")))
      .nickname("getImageFileById")
      .summary("Fetch an image with options to resize and crop")
      .notes("Fetches a image with options to resize and crop")
      .produces("application/octet-stream")
      .parameters(
        List[Parameter](pathParam[String]("id").description("The ID of the image"))
          ++ getImageParams:_*
      ).responseMessages(response404, response500)

    get("/:name", operation(getImageFile)) {
      getRawImage(params("name")) match {
        case Left(img) => Ok(img, staticAssetCacheHeaders)
        case Right(redirectUrl) => redirect(redirectUrl)
      }
    }

    get("/id/:id", operation(getImageFileById)) {
     imageRepository.withId(long("id")) match {
        case Some(imageMeta) =>
          val imageName = uriParse(imageMeta.imageUrl).toStringRaw.substring(1) // Strip heading '/'
          getRawImage(imageName) match {
            case Left(img) => Ok(img)
            case Right(redirectUrl) => redirect(redirectUrl)
          }
        case None => None
      }
    }

    private def getRawImage(imageName: String): Either[ImageStream, String] = {
      val imageUrl = s"/$imageName"
      imageStorage.get(imageName) match {
        case Success(img) if img.format.equals("gif") => Left(img)
        case Success(img) =>

          doubleOrNone("storedRatio")
            .flatMap(storedRatio => imageRepository.getStoredParametersFor(imageUrl, storedRatio.toString))
            match {
            case Some(storedParameters) => Right(url(converterService.asApiUrl(imageUrl),
              read[Map[String, String]](write(storedParameters.rawImageQueryParameters.copy(
                width = intOrNoneWithValidation("width"), height = intOrNoneWithValidation("height")))),
              absolutize = false
            ))
            case None =>
              val parameters = extractRawImageQueryParameters()
              val transformed = for {
                cropped <- crop(img, parameters)
                dynamicCropped <- dynamicCrop(cropped, parameters)
                resized <- resize(dynamicCropped, parameters)
              } yield resized
              Left(transformed.get)
          }
        case Failure(e) => throw e
      }
    }

    def extractRawImageQueryParameters()(implicit request: HttpServletRequest): RawImageQueryParameters = {
      RawImageQueryParameters(
        width = intOrNoneWithValidation("width"),
        height = intOrNoneWithValidation("height"),
        cropStartX = percentage("cropStartX"),
        cropStartY = percentage("cropStartY"),
        cropEndX = percentage("cropEndX"),
        cropEndY = percentage("cropEndY"),
        focalX = percentage("focalX"),
        focalY = percentage("focalY"),
        ratio = paramOrNone("ratio")
      )
    }

    def crop(image: ImageStream, parameters: RawImageQueryParameters): Try[ImageStream] = {
      (parameters.cropStartX, parameters.cropStartY, parameters.cropEndX, parameters.cropEndY) match {
        case (Some(sx), Some(sy), Some(ex), Some(ey)) =>
          imageConverter.crop(image, PercentPoint(sx.toInt, sy.toInt), PercentPoint(ex.toInt, ey.toInt))
        case _ => Success(image)
      }
    }

    def dynamicCrop(image: ImageStream, parameters: RawImageQueryParameters): Try[ImageStream] = {
      (parameters.focalX, parameters.focalY, parameters.width, parameters.height) match {
        case (Some(fx), Some(fy), w, h) =>
          imageConverter.dynamicCrop(image, PercentPoint(fx.toInt, fy.toInt), w, h, parameters.ratio.map(_.toDouble))
        case _ => Success(image)
      }
    }

    def resize(image: ImageStream, parameters: RawImageQueryParameters): Try[ImageStream] = {
      (parameters.width, parameters.height) match {
        case (Some(width), Some(height)) => imageConverter.resize(image, width.toInt, height.toInt)
        case (Some(width), _) => imageConverter.resizeWidth(image, width.toInt)
        case (_, Some(height)) => imageConverter.resizeHeight(image, height.toInt)
        case _ => Success(image)
      }
    }

  }
}
