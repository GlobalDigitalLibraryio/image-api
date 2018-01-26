/*
 * Part of NDLA image_api.
 * Copyright (C) 2017 NDLA
 *
 * See LICENSE
 */

package db.migration

import java.sql.Connection
import java.util.Date

import io.digitallibrary.language.model.LanguageTag
import org.flywaydb.core.api.migration.jdbc.JdbcMigration
import org.json4s.native.Serialization.{read, write}
import org.postgresql.util.PGobject
import scalikejdbc.{DB, DBSession, _}

import scala.util.Try

class V7__RemoveInvalidLanguages extends JdbcMigration {
  implicit val formats = org.json4s.DefaultFormats
  val defaultLanguage = LanguageTag("eng")

  override def migrate(connection: Connection): Unit = {
    val db = DB(connection)
    db.autoClose(false)

    db.withinTx { implicit session =>
      allImageMetadata.map(updateImageLanguage).foreach(update)
    }
  }

  def languageOrEnglish(language: Option[String]): String = {
    language match {
      case Some(lang) => Try(LanguageTag(lang)).getOrElse(defaultLanguage).toString
      case None => defaultLanguage.toString
    }
  }

  def updateImageLanguage(metaInformation: V7_ImageMetaInformation): V7_ImageMetaInformation = {
    metaInformation.copy(
      titles = metaInformation.titles.map(t => V7_ImageTitle(t.title, Some(languageOrEnglish(t.language)))),
      alttexts = metaInformation.alttexts.map(t => V7_ImageAltText(t.alttext, Some(languageOrEnglish(t.language)))),
      tags = metaInformation.tags.map(t => V7_ImageTag(t.tags, Some(languageOrEnglish(t.language)))),
      captions = metaInformation.captions.map(t => V7_ImageCaption(t.caption, Some(languageOrEnglish(t.language))))
    )
  }

  def allImageMetadata(implicit session: DBSession): List[V7_ImageMetaInformation] = {
    sql"select id, metadata from imagemetadata".map(rs => {
      val meta = read[V7_ImageMetaInformation](rs.string("metadata"))
      V7_ImageMetaInformation(
        Some(rs.long("id")),
        meta.titles,
        meta.alttexts,
        meta.imageUrl,
        meta.size,
        meta.contentType,
        meta.copyright,
        meta.tags,
        meta.captions,
        meta.updatedBy,
        meta.updated)
    }
    ).list().apply()
  }

  def update(imagemetadata: V7_ImageMetaInformation)(implicit session: DBSession) = {
    val dataObject = new PGobject()
    dataObject.setType("jsonb")
    dataObject.setValue(write(imagemetadata))

    sql"update imagemetadata set metadata = $dataObject where id = ${imagemetadata.id}".update().apply
  }

}


case class V7_ImageTitle(title: String, language: Option[String])
case class V7_ImageAltText(alttext: String, language: Option[String])
case class V7_ImageCaption(caption: String, language: Option[String])
case class V7_ImageTag(tags: Seq[String], language: Option[String])
case class V7_Image(fileName: String, size: Long, contentType: String)
case class V7_Copyright(license: V7_License, origin: String, authors: Seq[V5_Author])
case class V7_License(license: String, description: String, url: Option[String])
case class V7_Author(`type`: String, name: String)
case class V7_ImageMetaInformation(id: Option[Long],
                                   titles: Seq[V7_ImageTitle],
                                   alttexts: Seq[V7_ImageAltText],
                                   imageUrl: String,
                                   size: Long,
                                   contentType: String,
                                   copyright: V7_Copyright,
                                   tags: Seq[V7_ImageTag],
                                   captions: Seq[V7_ImageCaption],
                                   updatedBy: String,
                                   updated: Date)
