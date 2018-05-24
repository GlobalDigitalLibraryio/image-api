/*
 * Part of NDLA image_api.
 * Copyright (C) 2016 NDLA
 *
 * See LICENSE
 *
 */

package no.ndla.imageapi.repository

import com.typesafe.scalalogging.LazyLogging
import no.ndla.imageapi.controller.LanguageTagSerializer
import no.ndla.imageapi.integration.DataSource
import no.ndla.imageapi.model.api.StoredParameters
import no.ndla.imageapi.model.domain.{ImageMetaInformation, ParameterInformation}
import no.ndla.imageapi.service.ConverterService
import org.json4s.native.Serialization.write
import org.postgresql.util.PGobject
import scalikejdbc._


trait ImageRepository {
  this: DataSource with ConverterService =>
  val imageRepository: ImageRepository

  class ImageRepository extends LazyLogging {

    def insertOrUpdateStoredParameters(imageUrl: String, parameters: StoredParameters)(implicit session: DBSession = AutoSession): StoredParameters = {
      getStoredParametersFor(imageUrl, parameters.forRatio) match {
        case Some(_) => updateStoredParameters(imageUrl, parameters)
        case None => insertStoredParameters(imageUrl, parameters)
      }
    }

    def insertStoredParameters(imageUrl: String, parameters: StoredParameters)(implicit session: DBSession = AutoSession): StoredParameters = {
      val c = ParameterInformation.column
      val startRevision = 1
      val r = parameters.rawImageQueryParameters
      val count = QueryDSL.insertInto(ParameterInformation).namedValues(
        c.c("image_url") -> imageUrl,
        c.c("for_ratio") -> parameters.forRatio,
        c.c("width") -> r.width,
        c.c("height") -> r.height,
        c.c("crop_start_x") -> r.cropStartX,
        c.c("crop_start_y") -> r.cropStartY,
        c.c("crop_end_x") -> r.cropEndX,
        c.c("crop_end_y") -> r.cropEndY,
        c.c("focal_x") -> r.focalX,
        c.c("focal_y") -> r.focalY,
        c.c("ratio") -> r.ratio,
        c.revision -> startRevision
      ).toSQL.update().apply()
      if (count != 1) {
        throw new RuntimeException(s"Failed to insert raw image query parameters for imageUrl=$imageUrl")
      } else {
        parameters.copy(revision = Some(startRevision))
      }
    }

    def updateStoredParameters(imageUrl: String, parameters: StoredParameters)(implicit session: DBSession = AutoSession): StoredParameters = {
      val c = ParameterInformation.column
      val nextRevision = parameters.revision.map(_ + 1).getOrElse(1)
      val r = parameters.rawImageQueryParameters
      val count = QueryDSL.update(ParameterInformation).set(
        c.c("width") -> r.width,
        c.c("height") -> r.height,
        c.c("crop_start_x") -> r.cropStartX,
        c.c("crop_start_y") -> r.cropStartY,
        c.c("crop_end_x") -> r.cropEndX,
        c.c("crop_end_y") -> r.cropEndY,
        c.c("focal_x") -> r.focalX,
        c.c("focal_y") -> r.focalY,
        c.c("ratio") -> r.ratio,
        c.revision -> nextRevision
      ).where
        .eq(c.imageUrl, imageUrl).and
        .eq(c.forRatio, parameters.forRatio).and
        .eq(c.revision, parameters.revision)
        .toSQL.update().apply()
      if (count != 1) {
        throw new OptimisticLockException()
      } else {
        parameters.copy(revision = Some(nextRevision))
      }
    }

    def getStoredParameters(imageUrl: String)(implicit session: DBSession = ReadOnlyAutoSession): Option[Seq[StoredParameters]] = {
      val im = ParameterInformation.syntax("im")
      val results: Seq[StoredParameters] = sql"select ${im.result.*} from ${ParameterInformation.as(im)} where image_url = ${imageUrl}"
        .map(ParameterInformation(im)).list().apply()
      if (results.nonEmpty) {
        Some(results)
      } else {
        None
      }
    }

    def getStoredParametersFor(imageUrl: String, forRatio: String)(implicit session: DBSession = ReadOnlyAutoSession): Option[StoredParameters] = {
      val im = ParameterInformation.syntax("im")
      sql"select ${im.result.*} from ${ParameterInformation.as(im)} where image_url = ${imageUrl} and for_ratio = ${forRatio}"
        .map(ParameterInformation(im)).single().apply()
    }

    implicit val formats = org.json4s.DefaultFormats + ImageMetaInformation.JSonSerializer + new LanguageTagSerializer

    def withId(id: Long): Option[ImageMetaInformation] = {
      DB readOnly { implicit session =>
        imageMetaInformationWhere(sqls"im.id = $id")
      }
    }

   def getRandomImage()(implicit session: DBSession = ReadOnlyAutoSession): Option[ImageMetaInformation] = {
      val im = ImageMetaInformation.syntax("im")
      sql"select ${im.result.*} from ${ImageMetaInformation.as(im)} where metadata is not null order by random() limit 1".map(ImageMetaInformation(im)).single().apply()
    }

    def withExternalId(externalId: String): Option[ImageMetaInformation] = {
      DB readOnly { implicit session =>
        imageMetaInformationWhere(sqls"im.external_id = $externalId")
      }
    }

    def insert(imageMeta: ImageMetaInformation, externalId: Option[String] = None)(implicit session: DBSession = AutoSession) = {
      val dataObject = new PGobject()
      dataObject.setType("jsonb")
      dataObject.setValue(write(imageMeta))

      val imageId = externalId match {
        case Some(ext) => sql"insert into imagemetadata(external_id, metadata) values (${ext}, ${dataObject})".updateAndReturnGeneratedKey.apply
        case None => sql"insert into imagemetadata(metadata) values (${dataObject})".updateAndReturnGeneratedKey.apply
      }

      imageMeta.copy(id = Some(imageId))
    }

    def insertWithExternalId(imageMetaInformation: ImageMetaInformation, externalId: String): ImageMetaInformation = {
      val json = write(imageMetaInformation)

      val dataObject = new PGobject()
      dataObject.setType("jsonb")
      dataObject.setValue(json)

      DB localTx { implicit session =>
        val imageId = sql"insert into imagemetadata(external_id, metadata) values(${externalId}, ${dataObject})".updateAndReturnGeneratedKey.apply
        imageMetaInformation.copy(id = Some(imageId))
      }
    }

    def update(imageMetaInformation: ImageMetaInformation, id: Long): ImageMetaInformation = {
      val json = write(imageMetaInformation)
      val dataObject = new PGobject()
      dataObject.setType("jsonb")
      dataObject.setValue(json)

      DB localTx { implicit session =>
        sql"update imagemetadata set metadata = ${dataObject} where id = ${id}".update.apply
        imageMetaInformation.copy(id = Some(id))
      }
    }

    def delete(imageId: Long)(implicit session: DBSession = AutoSession) = {
      sql"delete from imagemetadata where id = ${imageId}".update.apply
    }

    def minMaxId: (Long, Long) = {
      DB readOnly { implicit session =>
        sql"select coalesce(MIN(id),0) as mi, coalesce(MAX(id),0) as ma from imagemetadata".map(rs => {
          (rs.long("mi"), rs.long("ma"))
        }).single().apply() match {
          case Some(minmax) => minmax
          case None => (0L, 0L)
        }
      }
    }

    def imagesWithIdBetween(min: Long, max: Long): List[ImageMetaInformation] = imageMetaInformationsWhere(sqls"im.id between $min and $max")

    private def imageMetaInformationWhere(whereClause: SQLSyntax)(implicit session: DBSession = ReadOnlyAutoSession): Option[ImageMetaInformation] = {
      val im = ImageMetaInformation.syntax("im")
      sql"select ${im.result.*} from ${ImageMetaInformation.as(im)} where $whereClause".map(ImageMetaInformation(im)).single().apply()
    }

    private def imageMetaInformationsWhere(whereClause: SQLSyntax)(implicit session: DBSession = ReadOnlyAutoSession): List[ImageMetaInformation] = {
      val im = ImageMetaInformation.syntax("im")
      sql"select ${im.result.*} from ${ImageMetaInformation.as(im)} where $whereClause".map(ImageMetaInformation(im)).list.apply()
    }
  }
}