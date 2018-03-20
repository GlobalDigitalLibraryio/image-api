/*
 * Part of NDLA image_api.
 * Copyright (C) 2016 NDLA
 *
 * See LICENSE
 *
 */

package no.ndla.imageapi

import com.amazonaws.ClientConfiguration
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}
import com.zaxxer.hikari.HikariDataSource
import io.digitallibrary.network.GdlClient
import no.ndla.imageapi.auth.{Role, User}
import no.ndla.imageapi.controller._
import no.ndla.imageapi.integration._
import no.ndla.imageapi.repository.ImageRepository
import no.ndla.imageapi.service._
import no.ndla.imageapi.service.search.{IndexBuilderService, IndexService, SearchConverterService, SearchService}
import scalikejdbc.{ConnectionPool, DataSourceConnectionPool}

object ComponentRegistry
  extends ElasticClient
  with IndexService
  with SearchService
  with SearchConverterService
  with DataSource
  with ImageRepository
  with WriteService
  with AmazonClient
  with ImageStorageService
  with IndexBuilderService
  with GdlClient
  with ConverterService
  with ValidationService
  with ImageControllerV2
  with RawController
  with InternController
  with HealthController
  with ImageConverter
  with User
  with Role
  with Clock
{
  implicit val swagger = new ImageSwagger

  lazy val dataSource = new HikariDataSource()
  dataSource.setJdbcUrl(ImageApiProperties.DBConnectionUrl)
  dataSource.setUsername(ImageApiProperties.MetaUserName)
  dataSource.setPassword(ImageApiProperties.MetaPassword)
  dataSource.setMaximumPoolSize(ImageApiProperties.MetaMaxConnections)
  dataSource.setSchema(ImageApiProperties.MetaSchema)
  ConnectionPool.singleton(new DataSourceConnectionPool(dataSource))

  val amazonClient: AmazonS3 = {
    val commonClient = AmazonS3ClientBuilder
      .standard()
      .withClientConfiguration(
        new ClientConfiguration()
          .withTcpKeepAlive(false)
      )

    (ImageApiProperties.Environment match {
      case "local" =>
        commonClient
          .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration("http://minio.gdl-local:9000", Regions.EU_CENTRAL_1.name()))
          .withPathStyleAccessEnabled(true)
      case _ =>
        commonClient.withRegion(Regions.EU_CENTRAL_1)
    }).build()
  }

  lazy val indexService = new IndexService
  lazy val searchService = new SearchService
  lazy val indexBuilderService = new IndexBuilderService
  lazy val imageRepository = new ImageRepository
  lazy val writeService = new WriteService
  lazy val validationService = new ValidationService
  lazy val imageStorage = new AmazonImageStorageService
  lazy val gdlClient = new GdlClient
  lazy val imageControllerV2 = new ImageControllerV2
  lazy val rawController = new RawController
  lazy val internController = new InternController
  lazy val healthController = new HealthController
  lazy val resourcesApp = new ResourcesApp
  lazy val converterService = new ConverterService
  lazy val esClient = EsClientFactory.getClient()
  lazy val searchConverterService = new SearchConverterService

  lazy val imageConverter = new ImageConverter
  lazy val authUser = new AuthUser
  lazy val authRole = new AuthRole
  lazy val clock = new SystemClock
}
