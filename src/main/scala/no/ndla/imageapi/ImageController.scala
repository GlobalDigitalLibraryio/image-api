package no.ndla.imageapi

import com.typesafe.scalalogging.LazyLogging
import no.ndla.imageapi.business.{ImageMeta, ImageStorage}
import no.ndla.imageapi.integration.AmazonIntegration
import no.ndla.imageapi.model.Error._
import no.ndla.imageapi.model.{Error, ImageMetaInformation, ImageMetaSummary}
import no.ndla.logging.LoggerContext
import org.json4s.{DefaultFormats, Formats}
import org.scalatra.ScalatraServlet
import org.scalatra.json._
import org.scalatra.swagger.{Swagger, SwaggerSupport}

class ImageController (implicit val swagger:Swagger) extends ScalatraServlet with NativeJsonSupport with SwaggerSupport with LazyLogging {

  protected implicit override val jsonFormats: Formats = DefaultFormats

  // Swagger-stuff
  protected val applicationDescription = "API for accessing images from ndla.no."

  val getImages =
    (apiOperation[List[ImageMetaSummary]]("getImages")
      summary "Show all images"
      notes "Shows all the images in the ndla.no database. You can search it too."
      parameters (
        headerParam[Option[String]]("X-Correlation-ID").description("User supplied correlation-id. May be omitted."),
        headerParam[Option[String]]("app-key").description("Your app-key. May be omitted to access api anonymously, but rate limiting applies on anonymous access."),
        queryParam[Option[String]]("tags").description("Return only images with submitted tag. Multiple tags may be entered comma separated, and will give results matching either one of them."),
        queryParam[Option[String]]("minimumSize").description("Return only images with full size larger than submitted value in bytes."),
        queryParam[Option[String]]("lang").description("The ISO 639-1 language code describing language used in query-params.")
      ))

  val getByImageId =
    (apiOperation[ImageMetaInformation]("findByImageId")
      summary "Show image info"
      notes "Shows info of the image with submitted id."
      parameters (
        headerParam[Option[String]]("X-Correlation-ID").description("User supplied correlation-id. May be omitted."),
        pathParam[String]("image_id").description("Image_id of the image that needs to be fetched."))
      )


  // Before every action runs, set the content type to be in JSON format.
  before() {
    contentType = formats("json")
    LoggerContext.setCorrelationID(Option(request.getHeader("X-Correlation-ID")))
  }

  after() {
    LoggerContext.clearCorrelationID();
  }

  error{
    case t:Throwable => {
      logger.error(Error.GenericError.toString, t)
      halt(status = 500, body = Error.GenericError)
    }
  }

  val imageMeta: ImageMeta = AmazonIntegration.getImageMeta()
  val imageStorage: ImageStorage = AmazonIntegration.getImageStorage()


  get("/", operation(getImages)) {
    val minimumSize = params.get("minimumSize")
    val tags = params.get("tags")
    val lang = params.get("lang")
    logger.info("GET / with params minimumSize='{}', tags='{}', lang={}", minimumSize, tags, lang)

    val size = minimumSize match {
      case Some(size) => if (size.forall(_.isDigit)) Option(size.toInt) else None
      case None => None
    }

    tags match {
      case Some(tags) => imageMeta.withTags(
        tags = tags.toLowerCase().split(",").map(_.trim),
        minimumSize = size,
        language = lang)

      case None => imageMeta.all(minimumSize = size)
    }
  }

  get("/:image_id", operation(getByImageId)) {
    val imageId = params("image_id")
    logger.info("GET /{}", imageId)

    if(imageId.forall(_.isDigit)) {
      imageMeta.withId(imageId) match {
        case Some(image) => image
        case None => halt(status = 404, body = Error(NOT_FOUND, s"Image with id $imageId not found"))
      }
    } else {
      halt(status = 404, body = Error(NOT_FOUND, s"Image with id $imageId not found"))
    }
  }

  get("/thumbs/:name") {
    val name = params("name")
    logger.info("GET /thumbs/{}", name)

    imageStorage.get(s"thumbs/$name") match {
      case Some(image) => {
        contentType = image._1
        image._2
      }
      case None => halt(status = 404, body = Error(NOT_FOUND, s"Image with key /thumbs/$name not found"))
    }
  }

  get("/full/:name") {
    val name = params("name")
    logger.info("GET /full/{}", name)

    imageStorage.get(s"full/$name") match {
      case Some(image) => {
        contentType = image._1
        image._2
      }
      case None => halt(status = 404, body = Error(NOT_FOUND, s"Image with key /full/$name not found"))
    }
  }
}