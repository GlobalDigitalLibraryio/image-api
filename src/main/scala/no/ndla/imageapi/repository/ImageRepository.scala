/*
 * Part of NDLA image_api.
 * Copyright (C) 2016 NDLA
 *
 * See LICENSE
 *
 */

package no.ndla.imageapi.repository

import com.typesafe.scalalogging.LazyLogging
import no.ndla.imageapi.controller.{LanguageTagSerializer, LicenseSerializer}
import no.ndla.imageapi.integration.DataSource
import no.ndla.imageapi.model.api
import no.ndla.imageapi.model.api.StoredParameters
import no.ndla.imageapi.model.domain.{ImageMetaInformation, ImageVariant, ParameterInformation}
import no.ndla.imageapi.service.ConverterService
import org.json4s.native.Serialization.write
import org.postgresql.util.PGobject
import scalikejdbc._

import scala.util.Try


trait ImageRepository {
  this: DataSource with ConverterService =>
  val imageRepository: ImageRepository

  class ImageRepository extends LazyLogging {


    implicit val formats = org.json4s.DefaultFormats + ImageMetaInformation.JSonSerializer + new LanguageTagSerializer + new LicenseSerializer

    def withId(id: Long): Option[ImageMetaInformation] = {
      DB readOnly { implicit session =>
        imageMetaInformationWhere(sqls"im.id = $id")
      }
    }

    def insertImageVariant(variant: ImageVariant)(implicit session: DBSession = AutoSession): Try[ImageVariant] = {
      val iv = ImageVariant.column
      val startRevision = 1

      Try{
        insert.into(ImageVariant).namedValues(
          iv.imageUrl -> variant.imageUrl,
          iv.ratio -> variant.ratio,
          iv.revision -> startRevision,
          iv.topLeftX -> variant.topLeftX,
          iv.topLeftY -> variant.topLeftY,
          iv.width -> variant.width,
          iv.height -> variant.height
        ).toSQL.update().apply()

        variant.copy(revision = Some(startRevision))
      }
    }

    def updateImageVariant(variant: ImageVariant)(implicit session: DBSession = AutoSession): Try[ImageVariant] = {
      val iv = ImageVariant.column
      val newRevision = variant.revision.getOrElse(0) + 1

      Try {
        val count = update(ImageVariant).set(
          iv.revision -> newRevision,
          iv.ratio -> variant.ratio,
          iv.topLeftX -> variant.topLeftX,
          iv.topLeftY -> variant.topLeftY,
          iv.width -> variant.width,
          iv.height -> variant.height
        ).where
          .eq(iv.imageUrl, variant.imageUrl).and
          .eq(iv.ratio, variant.ratio).and
          .eq(iv.revision, variant.revision).toSQL.update().apply()

        if(count != 1){
          throw new OptimisticLockException()
        } else {
          variant.copy(revision = Some(newRevision))
        }
      }
    }

    def getImageVariants(imageUrl: String)(implicit session: DBSession = ReadOnlyAutoSession): Map[String, ImageVariant] = {
      val iv = ImageVariant.syntax
      select
        .from(ImageVariant as iv)
        .where.eq(iv.imageUrl, imageUrl).toSQL
        .map(ImageVariant(iv)).list().apply()
        .map(r => r.ratio -> r).toMap
    }

    def getImageVariant(imageUrl: String, ratio: String)(implicit session: DBSession = ReadOnlyAutoSession): Option[ImageVariant] = {
      val iv = ImageVariant.syntax
      select
        .from(ImageVariant as iv)
        .where.eq(iv.imageUrl, imageUrl)
        .and.eq(iv.ratio, ratio).toSQL
        .map(ImageVariant(iv)).single().apply()
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

    def insertImageMeta(imageMeta: ImageMetaInformation, externalId: Option[String] = None)(implicit session: DBSession = AutoSession) = {
      val dataObject = new PGobject()
      dataObject.setType("jsonb")
      dataObject.setValue(write(imageMeta))

      val imageId = externalId match {
        case Some(ext) => sql"insert into imagemetadata(external_id, metadata, storage_service) values (${ext}, ${dataObject}, ${imageMeta.storageService.toString})".updateAndReturnGeneratedKey.apply
        case None => sql"insert into imagemetadata(metadata, storage_service) values (${dataObject}, ${imageMeta.storageService.toString})".updateAndReturnGeneratedKey.apply
      }

      imageMeta.copy(id = Some(imageId))
    }

    def insertWithExternalId(imageMetaInformation: ImageMetaInformation, externalId: String): ImageMetaInformation = {
      val json = write(imageMetaInformation)

      val dataObject = new PGobject()
      dataObject.setType("jsonb")
      dataObject.setValue(json)

      DB localTx { implicit session =>
        val imageId = sql"insert into imagemetadata(external_id, metadata, storage_service) values(${externalId}, ${dataObject}, ${imageMetaInformation.storageService.toString})".updateAndReturnGeneratedKey.apply
        imageMetaInformation.copy(id = Some(imageId))
      }
    }

    def updateImageMeta(imageMetaInformation: ImageMetaInformation, id: Long): ImageMetaInformation = {
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