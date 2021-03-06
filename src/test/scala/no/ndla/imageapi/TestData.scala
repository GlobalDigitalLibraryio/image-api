/*
 * Part of NDLA image_api.
 * Copyright (C) 2016 NDLA
 *
 * See LICENSE
 *
 */

package no.ndla.imageapi

import java.awt.image.BufferedImage
import java.io.InputStream

import io.digitallibrary.language.model.LanguageTag
import io.digitallibrary.license.model.License
import javax.imageio.ImageIO
import no.ndla.imageapi.model.api
import no.ndla.imageapi.model.domain._
import org.joda.time.{DateTime, DateTimeZone}

/**
  * Testklasse (og kanskje et utgangspunkt for en mer permanent løsning) som
  * kan benyttes til å laste opp bilder til en S3-bucket, samt metainformasjon til en DynamoDB-instans
  */
object TestData {

  def updated() = (new DateTime(2017, 4, 1, 12, 15, 32, DateTimeZone.UTC)).toDate

  val ByNcSa = License("cc-by-nc-sa-4.0")

  val nob = LanguageTag("nb")

  val elg = ImageMetaInformation(Some(1), None, List(ImageTitle("Elg i busk", nob)), List(ImageAltText("Elg i busk", nob)),
    "Elg.jpg", 2865539, "image/jpeg",
    Copyright(ByNcSa, "http://www.scanpix.no", List(Author("Fotograf", "Test Testesen")), List(Author("Redaksjonelt", "Kåre Knegg")), List(Author("Leverandør", "Leverans Leveransensen")), None, None, None),
    List(ImageTag(List("rovdyr", "elg"), nob)), List(ImageCaption("Elg i busk", nob)), "ndla124", updated(), Some(StorageService.CLOUDINARY))

  val apiElg = api.ImageMetaInformationV2("1", None, "Elg.jpg", api.ImageTitle("Elg i busk", nob), api.ImageAltText("Elg i busk", nob),
    "Elg.jpg", 2865539, "image/jpeg", api.Copyright(api.License(ByNcSa.name, ByNcSa.description, Some(ByNcSa.url)),
      "http://www.scanpix.no", List(api.Author("Fotograf", "Test Testesen")), List(), List(), None, None, None),
    api.ImageTag(List("rovdyr", "elg"), nob), api.ImageCaption("Elg i busk", nob), List(nob), None)

  val bjorn = ImageMetaInformation(Some(2), None, List(ImageTitle("Bjørn i busk", nob)),List(ImageAltText("Elg i busk", nob)),
    "Bjørn.jpg", 141134, "image/jpeg",
    Copyright(ByNcSa, "http://www.scanpix.no", List(Author("Fotograf", "Test Testesen")), List(), List(), None, None, None),
    List(ImageTag(List("rovdyr", "bjørn"), nob)), List(ImageCaption("Bjørn i busk", nob)), "ndla124", updated(), Some(StorageService.CLOUDINARY))

  val jerv = ImageMetaInformation(Some(3), None, List(ImageTitle("Jerv på stein", nob)), List(ImageAltText("Elg i busk", nob)),
    "Jerv.jpg", 39061, "image/jpeg",
    Copyright(ByNcSa, "http://www.scanpix.no", List(Author("Fotograf", "Test Testesen")), List(), List(), None, None, None),
    List(ImageTag(List("rovdyr", "jerv"), nob)), List(ImageCaption("Jerv på stein", nob)), "ndla124", updated(), Some(StorageService.CLOUDINARY))

  val mink = ImageMetaInformation(Some(4), None, List(ImageTitle("Overrasket mink", nob)), List(ImageAltText("Elg i busk", nob)),
    "Mink.jpg", 102559, "image/jpeg",
    Copyright(ByNcSa, "http://www.scanpix.no", List(Author("Fotograf", "Test Testesen")), List(), List(), None, None, None),
    List(ImageTag(List("rovdyr", "mink"), nob)), List(ImageCaption("Overrasket mink", nob)), "ndla124", updated(), Some(StorageService.CLOUDINARY))

  val rein = ImageMetaInformation(Some(5), None, List(ImageTitle("Rein har fanget rødtopp", nob)), List(ImageAltText("Elg i busk", nob)),
    "Rein.jpg", 504911, "image/jpeg",
    Copyright(ByNcSa, "http://www.scanpix.no", List(Author("Fotograf", "Test Testesen")), List(), List(), None, None, None),
    List(ImageTag(List("rovdyr", "rein", "jakt"), nob)), List(ImageCaption("Rein har fanget rødtopp", nob)), "ndla124", updated(), Some(StorageService.CLOUDINARY))

  val nonexisting = ImageMetaInformation(Some(6), None, List(ImageTitle("Krokodille på krok", nob)), List(ImageAltText("Elg i busk", nob)),
    "Krokodille.jpg", 2865539, "image/jpeg",
    Copyright(ByNcSa, "http://www.scanpix.no", List(Author("Fotograf", "Test Testesen")), List(), List(), None, None, None),
    List(ImageTag(List("rovdyr", "krokodille"), nob)), List(ImageCaption("Krokodille på krok", nob)), "ndla124", updated(), Some(StorageService.CLOUDINARY))

  val nonexistingWithoutThumb = ImageMetaInformation(Some(6), None, List(ImageTitle("Bison på sletten", nob)), List(ImageAltText("Elg i busk", nob)),
    "Bison.jpg", 2865539, "image/jpeg",
    Copyright(ByNcSa, "http://www.scanpix.no", List(Author("Fotograf", "Test Testesen")), List(), List(), None, None, None),
    List(ImageTag(List("bison"), nob)), List(ImageCaption("Bison på sletten", nob)), "ndla124", updated(), Some(StorageService.CLOUDINARY))

  val testdata = List(elg, bjorn, jerv, mink, rein)

  case class DiskImage(filename: String) extends ImageStream {
    override def contentType: String = s"image/$format"

    override def stream: InputStream = getClass.getResourceAsStream(s"/$filename")

    override def fileName: String = filename

    override lazy val sourceImage: BufferedImage = ImageIO.read(stream)
  }

  val NdlaLogoImage = DiskImage("ndla_logo.jpg")
  val NdlaLogoGIFImage = DiskImage("ndla_logo.gif")

  val ChildrensImage = DiskImage("children-drawing-582306_640.jpg") // From https://pixabay.com/en/children-drawing-home-tree-meadow-582306/

}
