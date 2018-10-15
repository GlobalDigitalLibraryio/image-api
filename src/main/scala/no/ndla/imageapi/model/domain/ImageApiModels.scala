/*
 * Part of NDLA image_api.
 * Copyright (C) 2016 NDLA
 *
 * See LICENSE
 *
 */

package no.ndla.imageapi.model.domain

import java.util.Date

import io.digitallibrary.language.model.LanguageTag
import io.digitallibrary.license.model.License
import no.ndla.imageapi.ImageApiProperties
import no.ndla.imageapi.controller.{LanguageTagSerializer, LicenseSerializer}
import no.ndla.imageapi.model.api.{RawImageQueryParameters, StoredParameters}
import org.json4s.{FieldSerializer, Formats}
import org.json4s.FieldSerializer._
import org.json4s.native.Serialization._
import scalikejdbc._

case class ImageTitle(title: String, language: LanguageTag) extends LanguageField[String] { override def value: String = title }
case class ImageAltText(alttext: String, language: LanguageTag) extends LanguageField[String] { override def value: String = alttext }
case class ImageCaption(caption: String, language: LanguageTag) extends LanguageField[String] { override def value: String = caption }
case class ImageTag(tags: Seq[String], language: LanguageTag) extends LanguageField[Seq[String]] { override def value: Seq[String] = tags }
case class Image(fileName: String, size: Long, contentType: String)
case class Copyright(license: License, origin: String, creators: Seq[Author], processors: Seq[Author], rightsholders: Seq[Author], agreementId: Option[Long], validFrom: Option[Date], validTo: Option[Date])
case class Author(`type`: String, name: String)
case class ImageVariant(imageUrl: String, ratio: String, revision: Option[Int], topLeftX: Int, topLeftY: Int, width: Int, height: Int)
case class ImageMetaInformation(
  id: Option[Long],
  externalId: Option[String],
  titles: Seq[ImageTitle],
  alttexts: Seq[ImageAltText],
  imageUrl: String,
  size: Long,
  contentType: String,
  copyright: Copyright,
  tags: Seq[ImageTag],
  captions: Seq[ImageCaption],
  updatedBy: String,
  updated: Date,
  storageService: Option[StorageService.Value]
)

object ParameterInformation extends SQLSyntaxSupport[StoredParameters] {
  implicit val formats: Formats = org.json4s.DefaultFormats
  override val tableName = "parameters"
  override val schemaName = Some(ImageApiProperties.MetaSchema)
  def apply(im: SyntaxProvider[StoredParameters])(rs: WrappedResultSet): StoredParameters = apply(im.resultName)(rs)
  def apply(im: ResultName[StoredParameters])(rs: WrappedResultSet): StoredParameters = {
    StoredParameters(
      imageUrl = rs.string(im.c("image_url")),
      forRatio = rs.string(im.c("for_ratio")),
      revision = rs.intOpt(im.c("revision")),
      rawImageQueryParameters = RawImageQueryParameters(
        width = rs.intOpt(im.c("width")),
        height = rs.intOpt(im.c("height")),
        cropStartX = rs.intOpt(im.c("crop_start_x")),
        cropStartY = rs.intOpt(im.c("crop_start_y")),
        cropEndX = rs.intOpt(im.c("crop_end_x")),
        cropEndY = rs.intOpt(im.c("crop_end_y")),
        focalX = rs.intOpt(im.c("focal_x")),
        focalY = rs.intOpt(im.c("focal_y")),
        ratio = rs.stringOpt(im.c("ratio")))
    )
  }
}

object ImageVariant extends SQLSyntaxSupport[ImageVariant] {
  implicit val formats: Formats = org.json4s.DefaultFormats
  override val tableName = "image_variants"
  override val schemaName = Some(ImageApiProperties.MetaSchema)

  def apply(im: SyntaxProvider[ImageVariant])(rs: WrappedResultSet): ImageVariant = apply(im.resultName)(rs)
  def apply(im: ResultName[ImageVariant])(rs: WrappedResultSet): ImageVariant = {
    ImageVariant(
      rs.string(im.c("image_url")),
      rs.string(im.c("ratio")),
      rs.intOpt(im.c("revision")),
      rs.int(im.c("top_left_x")),
      rs.int(im.c("top_left_y")),
      rs.int(im.c("width")),
      rs.int(im.c("height")))
  }
}

object ImageMetaInformation extends SQLSyntaxSupport[ImageMetaInformation] {
  implicit val formats: Formats = org.json4s.DefaultFormats + new LanguageTagSerializer + new LicenseSerializer
  override val tableName = "imagemetadata"
  override val schemaName = Some(ImageApiProperties.MetaSchema)
  val JSonSerializer: FieldSerializer[ImageMetaInformation] = FieldSerializer[ImageMetaInformation](ignore("id"))

  def apply(im: SyntaxProvider[ImageMetaInformation])(rs: WrappedResultSet): ImageMetaInformation = apply(im.resultName)(rs)

  def apply(im: ResultName[ImageMetaInformation])(rs: WrappedResultSet): ImageMetaInformation = {
    val meta = read[ImageMetaInformation](rs.string(im.c("metadata")))
    ImageMetaInformation(Some(rs.long(im.c("id"))), rs.stringOpt(im.c("external_id")), meta.titles, meta.alttexts, meta.imageUrl, meta.size, meta.contentType,
      meta.copyright, meta.tags, meta.captions, meta.updatedBy, meta.updated, Some(StorageService.valueOf(rs.string(im.c("storage_service")))))
  }
}

case class ReindexResult(totalIndexed: Int, millisUsed: Long)
case class CloudinaryInfo(publicId: String, format: String, resource_type: String, height: Long, width: Long, bytes: Long, url: String)
object MediaType extends Enumeration {
  val XHTML: MediaType.Value = Value("application/xhtml+xml")
  val JPEG: MediaType.Value = Value("image/jpeg")
  val GIF: MediaType.Value = Value("image/gif")
  val PNG: MediaType.Value = Value("image/png")
  val CSS: MediaType.Value = Value("text/css")
  val EPUB: MediaType.Value = Value("application/epub+zip")

  def fromFileExtension(ext: String): MediaType.Value = {
    MediaType.values.find(_.toString.equalsIgnoreCase(ext)).getOrElse(throw new RuntimeException("Unsupported image extension"))
  }
}

object StorageService extends Enumeration {
  val AWS, CLOUDINARY = Value

  def valueOf(s: String): StorageService.Value = {
    StorageService.values.find(_.toString == s.toUpperCase).getOrElse(CLOUDINARY)
  }
}