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
import no.ndla.imageapi.ImageApiProperties
import no.ndla.imageapi.controller.LanguageTagSerializer
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
case class License(license: String, description: String, url: Option[String])
case class Author(`type`: String, name: String)
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
  updated: Date
)

case class RawImageQueryParameters(width: Option[Int], height: Option[Int], cropStartX: Option[Int], cropStartY: Option[Int], cropEndX: Option[Int], cropEndY: Option[Int], focalX: Option[Int], focalY: Option[Int], ratio: Option[String])
case class StoredRawImageQueryParameters(imageName: String, forRatio: String, parameters: RawImageQueryParameters)


object StoredParameters extends SQLSyntaxSupport[StoredRawImageQueryParameters] {
  implicit val formats: Formats = org.json4s.DefaultFormats
  override val tableName = "parameters"
  override val schemaName = Some(ImageApiProperties.MetaSchema)
  def apply(im: SyntaxProvider[StoredRawImageQueryParameters])(rs: WrappedResultSet): StoredRawImageQueryParameters = apply(im.resultName)(rs)
  def apply(im: ResultName[StoredRawImageQueryParameters])(rs: WrappedResultSet): StoredRawImageQueryParameters = {
    StoredRawImageQueryParameters(
      imageName = rs.string(im.c("image_name")),
      forRatio = rs.string(im.c("for_ratio")),
      parameters = RawImageQueryParameters(
        width = rs.intOpt(im.c("width")),
        height = rs.intOpt(im.c("height")),
        cropStartX = rs.intOpt(im.c("crop_start_x")),
        cropStartY = rs.intOpt(im.c("crop_start_y")),
        cropEndX = rs.intOpt(im.c("crop_end_x")),
        cropEndY = rs.intOpt(im.c("crop_end_y")),
        focalX = rs.intOpt(im.c("focal_x")),
        focalY = rs.intOpt(im.c("focal_y")),
        ratio = rs.stringOpt(im.c("ratio"))
      ))
  }
}

object ImageMetaInformation extends SQLSyntaxSupport[ImageMetaInformation] {
  implicit val formats: Formats = org.json4s.DefaultFormats + new LanguageTagSerializer
  override val tableName = "imagemetadata"
  override val schemaName = Some(ImageApiProperties.MetaSchema)
  val JSonSerializer: FieldSerializer[ImageMetaInformation] = FieldSerializer[ImageMetaInformation](ignore("id"))

  def apply(im: SyntaxProvider[ImageMetaInformation])(rs: WrappedResultSet): ImageMetaInformation = apply(im.resultName)(rs)

  def apply(im: ResultName[ImageMetaInformation])(rs: WrappedResultSet): ImageMetaInformation = {
    val meta = read[ImageMetaInformation](rs.string(im.c("metadata")))
    ImageMetaInformation(Some(rs.long(im.c("id"))), rs.stringOpt(im.c("external_id")), meta.titles, meta.alttexts, meta.imageUrl, meta.size, meta.contentType,
      meta.copyright, meta.tags, meta.captions, meta.updatedBy, meta.updated)
  }
}

case class ReindexResult(totalIndexed: Int, millisUsed: Long)