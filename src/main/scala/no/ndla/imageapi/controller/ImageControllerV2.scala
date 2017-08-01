/*
 * Part of NDLA image_api.
 * Copyright (C) 2016 NDLA
 *
 * See LICENSE
 *
 */

package no.ndla.imageapi.controller

import no.ndla.imageapi.ImageApiProperties.{MaxImageFileSizeBytes, RoleWithWriteAccess}
import no.ndla.imageapi.auth.Role
import no.ndla.imageapi.model.{ValidationException, ValidationMessage}
import no.ndla.imageapi.model.api.{Error, ImageMetaInformation, NewImageMetaInformation, SearchParams, SearchResult, ValidationError}
import no.ndla.imageapi.model.Language.{DefaultLanguage, AllLanguages}
import no.ndla.imageapi.repository.ImageRepository
import no.ndla.imageapi.service.search.SearchService
import no.ndla.imageapi.service.{ConverterService, WriteService}
import org.json4s.native.Serialization.read
import org.json4s.{DefaultFormats, Formats}
import org.scalatra.servlet.{FileUploadSupport, MultipartConfig}
import org.scalatra.swagger.DataType.ValueDataType
import org.scalatra.swagger._

import scala.util.{Failure, Success, Try}

trait ImageControllerV2 {
  this: ImageRepository with SearchService with ConverterService with WriteService with Role =>
  val imageControllerV2: ImageControllerV2

  class ImageControllerV2(implicit val swagger: Swagger) extends NdlaController with SwaggerSupport with FileUploadSupport {
    // Swagger-stuff
    protected val applicationDescription = "API for accessing images from ndla.no."
    protected implicit override val jsonFormats: Formats = DefaultFormats

    // Additional models used in error responses
    registerModel[ValidationError]()
    registerModel[Error]()
    registerModel[NewImageMetaInformation]()

    val response403 = ResponseMessage(403, "Access Denied", Some("Error"))
    val response404 = ResponseMessage(404, "Not found", Some("Error"))
    val response400 = ResponseMessage(400, "Validation error", Some("ValidationError"))
    val response413 = ResponseMessage(413, "File too big", Some("Error"))
    val response500 = ResponseMessage(500, "Unknown error", Some("Error"))

    val getImages =
      (apiOperation[SearchResult]("getImages")
        summary "Show all images"
        notes "Shows all the images in the ndla.no database. You can search it too."
        parameters(
        headerParam[Option[String]]("X-Correlation-ID").description("User supplied correlation-id. May be omitted."),
        headerParam[Option[String]]("app-key").description("Your app-key. May be omitted to access api anonymously, but rate limiting may apply on anonymous access."),
        queryParam[Option[String]]("query").description("Return only images with titles, alt-texts or tags matching the specified query."),
        queryParam[Option[String]]("minimum-size").description("Return only images with full size larger than submitted value in bytes."),
        queryParam[Option[String]]("language").description("The ISO 639-1 language code describing language used in query-params."),
        queryParam[Option[String]]("license").description("Return only images with provided license."),
        queryParam[Option[Int]]("page").description("The page number of the search hits to display."),
        queryParam[Option[Int]]("page-size").description("The number of search hits to display for each page.")
      )
        authorizations "oauth2"
        responseMessages response500)

    val getImagesPost =
      (apiOperation[List[SearchResult]]("getImagesPost")
        summary "Show all images"
        notes "Shows all the images in the ndla.no database. You can search it too."
        parameters(
        headerParam[Option[String]]("X-Correlation-ID").description("User supplied correlation-id"),
        headerParam[Option[String]]("app-key").description("Your app-key"),
        bodyParam[SearchParams]
      )
        authorizations "oauth2"
        responseMessages(response400, response500))

    val getByImageId =
      (apiOperation[ImageMetaInformation]("findByImageId")
        summary "Show image info"
        notes "Shows info of the image with submitted id."
        parameters(
        headerParam[Option[String]]("X-Correlation-ID").description("User supplied correlation-id. May be omitted."),
        headerParam[Option[String]]("app-key").description("Your app-key. May be omitted to access api anonymously, but rate limiting may apply on anonymous access."),
        pathParam[String]("image_id").description("Image_id of the image that needs to be fetched."),
        queryParam[Option[String]]("language").description("The ISO 639-1 language code describing language used in query-params.")
      )
        authorizations "oauth2"
        responseMessages(response404, response500))

    val newImage =
      (apiOperation[ImageMetaInformation]("newImage")
        summary "Upload a new image file with meta data"
        notes "Upload a new image file with meta data"
        consumes "multipart/form-data"
        parameters(
        headerParam[Option[String]]("X-Correlation-ID").description("User supplied correlation-id. May be omitted."),
        headerParam[Option[String]]("app-key").description("Your app-key. May be omitted to access api anonymously, but rate limiting may apply on anonymous access."),
        formParam[String]("metadata").description("The metadata for the image file to submit. See NewImageMetaInformation."),
        Parameter(name = "file", `type` = ValueDataType("file"), description = Some("The image file(s) to upload"), paramType = ParamType.Form)
      )
        authorizations "oauth2"
        responseMessages(response400, response403, response413, response500))

    configureMultipartHandling(MultipartConfig(maxFileSize = Some(MaxImageFileSizeBytes)))

    private def search(minimumSize: Option[String], query: Option[String], language: Option[String], license: Option[String], pageSize: Option[Int], page: Option[Int]) = {
      val size = minimumSize match {
        case Some(toCheck) => if (toCheck.forall(_.isDigit)) Option(toCheck.toInt) else None
        case None => None
      }

      query match {
        case Some(searchString) => searchService.matchingQuery(
          query = searchString.trim,
          minimumSize = size,
          language = language,
          license = license,
          page,
          pageSize)

        case None => searchService.all(minimumSize = size, license = license, page, pageSize)
      }
    }

    get("/", operation(getImages)) {
      val minimumSize = params.get("minimum-size")
      val query = params.get("query")
      val language = params.get("language")
      val license = params.get("license")
      val pageSize = params.get("page-size").flatMap(ps => Try(ps.toInt).toOption)
      val page = params.get("page").flatMap(idx => Try(idx.toInt).toOption)


      search(minimumSize, query, language, license, pageSize, page)
    }

    post("/search/", operation(getImagesPost)) {
      val searchParams = extract[SearchParams](request.body)
      val minimumSize = searchParams.minimumSize.map(_.toString)
      val query = searchParams.query
      val language = searchParams.language
      val license = searchParams.license
      val pageSize = searchParams.pageSize
      val page = searchParams.page

      search(minimumSize, query, language, license, pageSize, page)
    }

    get("/:image_id", operation(getByImageId)) {
      val imageId = long("image_id")
      val language = paramOrDefault("language", AllLanguages)
      imageRepository.withId(imageId).flatMap(image => converterService.asApiImageMetaInformationWithApplicationUrlAndSingleLanguage(image, language)) match {
        case Some(image) => image
        case None => halt(status = 404, body = Error(Error.NOT_FOUND, s"Image with id $imageId and language $language not found"))
      }
    }

    post("/", operation(newImage)) {
      authRole.assertHasRole(RoleWithWriteAccess)

      val newImage = params.get("metadata")
        .map(extract[NewImageMetaInformation])
        .getOrElse(throw new ValidationException(errors=Seq(ValidationMessage("metadata", "The request must contain image metadata"))))

      val file = fileParams.getOrElse("file", throw new ValidationException(errors=Seq(ValidationMessage("file", "The request must contain an image file"))))

      writeService.storeNewImage(newImage, file) match {
        case Success(imageMeta) => imageMeta
        case Failure(e) => errorHandler(e)
      }
    }

  }
}