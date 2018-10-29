/*
 * Part of NDLA image_api.
 * Copyright (C) 2016 NDLA
 *
 * See LICENSE
 *
 */

package no.ndla.imageapi.controller

import io.digitallibrary.language.model.LanguageTag
import no.ndla.imageapi.ImageApiProperties.{MaxImageFileSizeBytes, RoleWithWriteAccess}
import no.ndla.imageapi.auth.{Role, User}
import no.ndla.imageapi.model.api.{Error, ImageMetaInformationV2, ImageUrl, ImageVariant, License, NewImageMetaInformationV2, SearchParams, SearchResult, StoredParameters, UpdateImageMetaInformation, ValidationError}
import no.ndla.imageapi.model.domain.Sort
import no.ndla.imageapi.model.{Language, ValidationException, ValidationMessage}
import no.ndla.imageapi.repository.ImageRepository
import no.ndla.imageapi.service.search.SearchService
import no.ndla.imageapi.service.{ConverterService, ImageUrlBuilder, WriteService}
import org.json4s.{DefaultFormats, Formats}
import org.scalatra.Ok
import org.scalatra.servlet.{FileUploadSupport, MultipartConfig}
import org.scalatra.swagger.DataType.ValueDataType
import org.scalatra.swagger._
import org.scalatra.util.NotNothing

import scala.util.{Failure, Success, Try}

trait ImageControllerV2 {
  this: ImageRepository with SearchService with ConverterService with WriteService with Role with User =>
  val imageControllerV2: ImageControllerV2

  class ImageControllerV2(implicit val swagger: Swagger) extends NdlaController with SwaggerSupport with FileUploadSupport {
    // Swagger-stuff
    protected val applicationDescription = "API for accessing images from digitallibrary.io."
    protected implicit override val jsonFormats: Formats = DefaultFormats + new LanguageTagSerializer

    // Additional models used in error responses
    registerModel[ValidationError]()
    registerModel[Error]()
    registerModel[NewImageMetaInformationV2]()

    val response403 = ResponseMessage(403, "Access Denied", Some("Error"))
    val response404 = ResponseMessage(404, "Not found", Some("Error"))
    val response400 = ResponseMessage(400, "Validation error", Some("ValidationError"))
    val response413 = ResponseMessage(413, "File too big", Some("Error"))
    val response500 = ResponseMessage(500, "Unknown error", Some("Error"))

    case class Param(paramName:String, description:String)

    private val correlationId = Param("X-Correlation-ID","User supplied correlation-id. May be omitted.")
    private val query = Param("query","Return only images with titles, alt-texts or tags matching the specified query.")
    private val minSize = Param("minimum-size","Return only images with full size larger than submitted value in bytes.")
    private val language = Param("language", "The BCP-47 language code describing language.")
    private val license = Param("license","Return only images with provided license.")
    private val sort = Param("sort",
      s"""The sorting used on results.
             The following are supported: ${Sort.values.mkString(",")}.
             Default is by ${Sort.ByRelevanceDesc} (desc) when query is set, and ${Sort.ByTitleAsc} (asc) when query is empty.""".stripMargin)
    private val pageNo = Param("page","The page number of the search hits to display.")
    private val pageSize = Param("page-size","The number of search hits to display for each page.")
    private val imageId = Param("image_id","Image_id of the image that needs to be fetched.")
    private val imageUrl = Param("image_url","Image URL of the image which parameters need to be fetched.")
    private val metadata = Param("metadata",
      """The metadata for the image file to submit. Format (as JSON):
            {
             title: String,
             alttext: String,
             tags: Array[String],
             caption: String,
             language: String
             }""".stripMargin)
    private val file = Param("file", "The image file(s) to upload")


    private def asQueryParam[T: Manifest: NotNothing](param: Param) = queryParam[T](param.paramName).description(param.description)
    private def asHeaderParam[T: Manifest: NotNothing](param: Param) = headerParam[T](param.paramName).description(param.description)
    private def asPathParam[T: Manifest: NotNothing](param: Param) = pathParam[T](param.paramName).description(param.description)
    private def asFormParam[T: Manifest: NotNothing](param: Param) = formParam[T](param.paramName).description(param.description)
    private def asFileParam(param: Param) = Parameter(name = param.paramName, `type` = ValueDataType("file"), description = Some(param.description), paramType = ParamType.Form)


    configureMultipartHandling(MultipartConfig(maxFileSize = Some(MaxImageFileSizeBytes)))

    private def search(minimumSize: Option[Int], query: Option[String], language: Option[LanguageTag], license: Option[String], sort: Option[Sort.Value], pageSize: Option[Int], page: Option[Int]) = {
      query match {
        case Some(searchString) => searchService.matchingQuery(
          searchString.trim,
          minimumSize,
          language,
          sort.getOrElse(Sort.ByRelevanceDesc),
          license,
          page,
          pageSize)

        case None => searchService.all(minimumSize, license, language, sort.getOrElse(Sort.ByTitleAsc), page, pageSize)
      }
    }

    val getImages =
      (apiOperation[SearchResult]("getImages")
        summary "Find images"
        notes "Find images in the ndla.no database."
        parameters(
          asHeaderParam[Option[String]](correlationId),
          asQueryParam[Option[String]](query),
          asQueryParam[Option[Int]](minSize),
          asQueryParam[Option[String]](language),
          asQueryParam[Option[String]](license),
          asQueryParam[Option[String]](sort),
          asQueryParam[Option[Int]](pageNo),
          asQueryParam[Option[Int]](pageSize)
        )
        responseMessages response500)

    get("/", operation(getImages)) {
      val minimumSize = intOrNone(this.minSize.paramName)
      val query = paramOrNone(this.query.paramName)
      val language = paramOrNone(this.language.paramName).map(LanguageTag(_)).getOrElse(Language.DefaultLanguage)
      val license = params.get(this.license.paramName)
      val pageSize = intOrNone(this.pageSize.paramName)
      val page = intOrNone(this.pageNo.paramName)
      val sort = Sort.valueOf(paramOrDefault(this.sort.paramName, ""))

      search(minimumSize, query, Some(language), license, sort, pageSize, page)
    }

    val getImagesPost =
      (apiOperation[List[SearchResult]]("getImagesPost")
        summary "Find images"
        notes "Find images in the ndla.no database."
        parameters(
          asHeaderParam[Option[String]](correlationId),
          bodyParam[SearchParams]
        )
        responseMessages(response400, response500))

    post("/search/", operation(getImagesPost)) {
      val searchParams = extract[SearchParams](request.body)
      val minimumSize = searchParams.minimumSize
      val query = searchParams.query
      val language = paramOrNone(this.language.paramName).map(LanguageTag(_)).getOrElse(Language.DefaultLanguage)
      val license = searchParams.license
      val pageSize = searchParams.pageSize
      val page = searchParams.page
      val sort = Sort.valueOf(searchParams.sort)

      search(minimumSize, query, Some(language), license, sort, pageSize, page)
    }

    val getByImageId =
      (apiOperation[ImageMetaInformationV2]("findByImageId")
        summary "Fetch information for image"
        notes "Shows info of the image with submitted id."
        parameters(
          asHeaderParam[Option[String]](correlationId),
          asPathParam[String](imageId),
          asQueryParam[String](language)
        )
        responseMessages(response404, response500))

    get("/:image_id", operation(getByImageId)) {
      val imageId = long(this.imageId.paramName)
      val language = paramOrNone(this.language.paramName).map(LanguageTag(_)).getOrElse(Language.DefaultLanguage)
      imageRepository.withId(imageId).flatMap(image => {

        converterService.asApiImageMetaInformationWithApplicationUrlAndSingleLanguage(image, language, imageRepository.getImageVariants(image.imageUrl))
      }) match {
        case Some(image) => image
        case None => halt(status = 404, body = Error(Error.NOT_FOUND, s"Image with id $imageId and language $language not found"))
      }
    }

    get("/:image_id/imageUrl/?") {
      val imageId = long(this.imageId.paramName)
      val width = intOrNoneWithValidation("width")
      val language = paramOrNone(this.language.paramName).map(LanguageTag(_)).getOrElse(Language.DefaultLanguage)
      imageRepository.withId(imageId) match {
        case None => halt(status = 404, body = Error(Error.NOT_FOUND, s"Image with id $imageId and language $language not found"))
        case Some(meta) =>
          val alttext = Language.findByLanguageOrBestEffort(meta.alttexts, language).map(_.alttext.trim) match {
            case Some(str) if !str.isEmpty => Some(str)
            case _ => None
          }
          ImageUrl(imageId.toString,
            ImageUrlBuilder.urlFor(meta, width),
            alttext)
      }
    }

    val newImage =
      (apiOperation[ImageMetaInformationV2]("newImage")
        summary "Upload a new image with meta information"
        notes "Upload a new image file with meta data"
        consumes "multipart/form-data"
        parameters(
          asHeaderParam[Option[String]](correlationId),
          asFormParam[String](metadata),
          asFileParam(file)
        )
        authorizations "oauth2"
        responseMessages(response400, response403, response413, response500))

    post("/", operation(newImage)) {
      authUser.assertHasId()
      authRole.assertHasRole(RoleWithWriteAccess)

      val newImage = params.get(this.metadata.paramName)
        .map(extract[NewImageMetaInformationV2])
        .getOrElse(throw new ValidationException(errors = Seq(ValidationMessage("metadata", "The request must contain image metadata"))))

      val file = fileParams.getOrElse(this.file.paramName, throw new ValidationException(errors = Seq(ValidationMessage("file", "The request must contain an image file"))))

      if (imageRepository.withExternalId(newImage.externalId).isDefined) {
        halt(status = 409, body = Error(Error.CONFLICT, s"An image with external id=${newImage.externalId} already exists"))
      }

      writeService.storeNewImage(newImage, file)
        .map(img => converterService.asApiImageMetaInformationWithApplicationUrlAndSingleLanguage(img, newImage.language, imageRepository.getImageVariants(img.imageUrl))) match {
        case Success(imageMeta) => imageMeta
        case Failure(e) => errorHandler(e)
      }
    }

    val updateImage =
      (apiOperation[ImageMetaInformationV2]("newImage")
        summary "Update an existing image with meta information"
        notes "Updates an existing image with meta data."
        consumes "form-data"
        parameters(
          asHeaderParam[Option[String]](correlationId),
          asPathParam[String](imageId),
          bodyParam[UpdateImageMetaInformation]("metadata").description("The metadata for the image file to submit."),
        )
        authorizations "oauth2"
        responseMessages(response400, response403, response500))

    patch("/:image_id", operation(updateImage)) {
      authUser.assertHasId()
      authRole.assertHasRole(RoleWithWriteAccess)
      val imageId = long(this.imageId.paramName)
      writeService.updateImage(imageId, extract[UpdateImageMetaInformation](request.body)) match {
        case Success(imageMeta) => imageMeta
        case Failure(e) => errorHandler(e)
      }
    }

    val getLicenses =
      (apiOperation[Seq[License]]("getLicenses")
        summary "Get all licenses"
        notes "Gets all licenses"
        )
    get("/licenses", operation(getLicenses)) {
      io.digitallibrary.license.model.LicenseList.licenses.map(lic => License(lic.name, lic.description, Some(lic.url)))
    }

    val getImageVariants =
      (apiOperation[ImageVariant]("getImageVariants")
        summary "Get the image variants"
        notes "Get all variants for an image"
        parameters(
        asHeaderParam[Option[String]](correlationId),
        asPathParam[String](imageId))
        responseMessages(response404, response400, response500))

    get("/:image_id/variants/?", operation(getImageVariants)) {
      val imageId = long(this.imageId.paramName)
      imageRepository.withId(imageId).map(_.imageUrl).map(imageRepository.getImageVariants).flatMap(converterService.asApiImageVariants) match {
        case None => halt(status = 404, body = Error(Error.NOT_FOUND, s"No variants found for image with id $imageId"))
        case Some(variants) => variants
      }
    }

    val postImageVariants =
      (apiOperation[ImageVariant]("postImageVariants")
        summary "Post a variant for an image"
        notes "Insert or update a variant for an image"
        parameters(
        asHeaderParam[Option[String]](correlationId),
        asPathParam[String](imageId),
        bodyParam[ImageVariant]
      )
        responseMessages(response404, response400, response500))

    post("/:image_id/variants/?", operation(postImageVariants)) {
      authUser.assertHasId()
      authRole.assertHasRole(RoleWithWriteAccess)
      val imageId = long(this.imageId.paramName)

      Try(extract[ImageVariant](request.body)).toOption match {
        case Some(imageVariant) => imageRepository.withId(imageId).map(_.imageUrl).map(url => writeService.storeImageVariant(url, imageVariant)) match {
          case Some(Success(updatedVariant)) => updatedVariant
          case Some(Failure(ex)) => errorHandler(ex)
          case None => halt(status = 404, body = Error(Error.NOT_FOUND, s"Image with id $imageId not found"))
        }
        case None => throw new ValidationException(errors = Seq(ValidationMessage("body", "Invalid body for parameters")))
      }
    }



  }

}
