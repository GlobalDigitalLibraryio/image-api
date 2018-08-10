package no.ndla.imageapi.service

import io.digitallibrary.language.model.LanguageTag
import io.digitallibrary.license.model.License
import no.ndla.imageapi.model.ValidationException
import no.ndla.imageapi.model.api.{RawImageQueryParameters, StoredParameters}
import no.ndla.imageapi.model.domain._
import no.ndla.imageapi.{TestEnvironment, UnitSuite}
import org.joda.time.{DateTime, DateTimeZone}
import org.mockito.Mockito._
import org.scalatra.servlet.FileItem

class ValidationServiceTest extends UnitSuite with TestEnvironment {
  override val validationService = new ValidationService

  val fileMock = mock[FileItem]
  def updated() = (new DateTime(2017, 4, 1, 12, 15, 32, DateTimeZone.UTC)).toDate

  val sampleImageMeta = ImageMetaInformation(Some(1), None, Seq.empty, Seq.empty, "image.jpg", 1024, "image/jpeg", Copyright(License("cc-by-2.0"), "", Seq.empty, Seq.empty, Seq.empty, None, None, None), Seq.empty, Seq.empty, "ndla124", updated())
  val nob = LanguageTag("nb")

  override def beforeEach = {
    reset(fileMock)
  }

  test("validateImageFile returns a validation message if file has an unknown extension") {
    val fileName = "image.asdf"
    when(fileMock.name).thenReturn(fileName)
    val Some(result) = validationService.validateImageFile(fileMock)

    result.message.contains(s"The file $fileName does not have a known file extension") should be (true)
  }

  test("validateImageFile returns a validation message if content type is unknown") {
    val fileName = "image.jpg"
    when(fileMock.name).thenReturn(fileName)
    when(fileMock.contentType).thenReturn(Some("text/html"))
    val Some(result) = validationService.validateImageFile(fileMock)

    result.message.contains(s"The file $fileName is not a valid image file.") should be (true)
  }

  test("validateImageFile returns None if image file is valid") {
    val fileName = "image.jpg"
    when(fileMock.name).thenReturn(fileName)
    when(fileMock.contentType).thenReturn(Some("image/jpeg"))
    validationService.validateImageFile(fileMock).isDefined should be (false)
  }

  test("validate returns a validation error if title contains html") {
    val imageMeta = sampleImageMeta.copy(titles=Seq(ImageTitle("<h1>title</h1>", LanguageTag("nb"))))
    val result = validationService.validate(imageMeta)
    val exception = result.failed.get.asInstanceOf[ValidationException]
    exception.errors.length should be (1)
    exception.errors.head.message.contains("contains illegal html-characters") should be (true)
  }

  test("validate returns success if title is valid") {
    val imageMeta = sampleImageMeta.copy(titles=Seq(ImageTitle("title", LanguageTag("en"))))
    validationService.validate(imageMeta).isSuccess should be (true)
  }



  test("validate returns a validation error if copyright origin contains html") {
    val imageMeta = sampleImageMeta.copy(copyright=Copyright(License("cc-by-2.0"), "<h1>origin</h1>", Seq.empty, Seq.empty, Seq.empty, None, None, None))
    val result = validationService.validate(imageMeta)
    val exception = result.failed.get.asInstanceOf[ValidationException]
    exception.errors.length should be (1)

    exception.errors.head.message.contains("The content contains illegal html-characters") should be (true)
  }

  test("validate returns a validation error if author contains html") {
    val imageMeta = sampleImageMeta.copy(copyright=Copyright(License("cc-by-2.0"), "", Seq(Author("originator", "<h1>Drumpf</h1>")), Seq.empty, Seq.empty, None, None, None))
    val result = validationService.validate(imageMeta)
    val exception = result.failed.get.asInstanceOf[ValidationException]
    exception.errors.length should be (1)

    exception.errors.head.message.contains("The content contains illegal html-characters") should be (true)
  }

  test("validate returns success if copyright is valid") {
    val imageMeta = sampleImageMeta.copy(copyright=Copyright(License("cc-by-2.0"), "ntb", Seq(Author("originator", "Drumpf")), Seq.empty, Seq.empty, None, None, None))
    validationService.validate(imageMeta).isSuccess should be (true)
  }

  test("validate returns error if authortype is invalid") {
    val imageMeta = sampleImageMeta.copy(copyright=Copyright(License("cc-by-2.0"), "ntb", Seq(Author("invalidType", "Drumpf")), Seq.empty, Seq.empty, None, None, None))
    val result = validationService.validate(imageMeta)
    val exception = result.failed.get.asInstanceOf[ValidationException]
    exception.errors.length should be (1)

    exception.errors.head.message.contains("Author is of illegal type. Must be one of originator, photographer, artist, editorial, writer, scriptwriter, reader, translator, director, illustrator, cowriter, composer") should be (true)
  }

  test("validate returns a validation error if tags contain html") {
    val imageMeta = sampleImageMeta.copy(tags=Seq(ImageTag(Seq("<h1>tag</h1>"), LanguageTag("en"))))
    val result = validationService.validate(imageMeta)
    val exception = result.failed.get.asInstanceOf[ValidationException]
    exception.errors.length should be (1)

    exception.errors.head.message.contains("The content contains illegal html-characters") should be (true)
  }

  test("validate returns success if tags are valid") {
    val imageMeta = sampleImageMeta.copy(tags=Seq(ImageTag(Seq("tag"), LanguageTag("en"))))
    validationService.validate(imageMeta).isSuccess should be (true)
  }

  test("validate returns a validation error if alt texts contain html") {
    val imageMeta = sampleImageMeta.copy(alttexts=Seq(ImageAltText("<h1>alt text</h1>", LanguageTag("en"))))
    val result = validationService.validate(imageMeta)
    val exception = result.failed.get.asInstanceOf[ValidationException]
    exception.errors.length should be (1)

    exception.errors.head.message.contains("The content contains illegal html-characters") should be (true)
  }

  test("validate returns success if alt texts are valid") {
    val imageMeta = sampleImageMeta.copy(alttexts=Seq(ImageAltText("alt text", LanguageTag("en"))))
    validationService.validate(imageMeta).isSuccess should be (true)
  }

  test("validate returns a validation error if captions contain html") {
    val imageMeta = sampleImageMeta.copy(captions=Seq(ImageCaption("<h1>caption</h1>", LanguageTag("en"))))
    val result = validationService.validate(imageMeta)
    val exception = result.failed.get.asInstanceOf[ValidationException]
    exception.errors.length should be (1)

    exception.errors.head.message.contains("The content contains illegal html-characters") should be (true)
  }

  test("validate returns success if captions are valid") {
    val imageMeta = sampleImageMeta.copy(captions=Seq(ImageCaption("caption", LanguageTag("en"))))
    validationService.validate(imageMeta).isSuccess should be (true)
  }

  test("validate returns a validation error if a percentage value is outside [0, 100]") {
    val p = StoredParameters(imageUrl = "/123.jpg", forRatio = "0.81", revision = Some(1), rawImageQueryParameters = RawImageQueryParameters(width = None, height = None, cropStartX = Some(101), cropStartY = Some(10), cropEndX = None, cropEndY = None, focalX = Some(50), focalY = Some(60), ratio = Some("0.81")))
    validationService.validateStoredParameters(p).isDefined should be (true)
  }

  test("validate returns a validation error if imageUrl doesn't start with a '/'") {
    val p = StoredParameters(imageUrl = "123.jpg", forRatio = "0.81", revision = Some(1), rawImageQueryParameters = RawImageQueryParameters(width = None, height = None, cropStartX = Some(10), cropStartY = Some(10), cropEndX = None, cropEndY = None, focalX = Some(50), focalY = Some(60), ratio = Some("0.81")))
    validationService.validateStoredParameters(p).isDefined should be (true)
  }

}
