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
import io.digitallibrary.network.GdlClient
import no.ndla.imageapi.auth.{Role, User}
import no.ndla.imageapi.controller._
import no.ndla.imageapi.integration._
import no.ndla.imageapi.repository.ImageRepository
import no.ndla.imageapi.service._
import no.ndla.imageapi.service.search.{IndexBuilderService, IndexService, SearchConverterService, SearchService}
import org.postgresql.ds.PGPoolingDataSource

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
  with MigrationApiClient
  with ConverterService
  with ValidationService
  with ImageController
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

  val dataSource = new PGPoolingDataSource()
  dataSource.setUser(ImageApiProperties.MetaUserName)
  dataSource.setPassword(ImageApiProperties.MetaPassword)
  dataSource.setDatabaseName(ImageApiProperties.MetaResource)
  dataSource.setServerName(ImageApiProperties.MetaServer)
  dataSource.setPortNumber(ImageApiProperties.MetaPort)
  dataSource.setInitialConnections(ImageApiProperties.MetaInitialConnections)
  dataSource.setMaxConnections(ImageApiProperties.MetaMaxConnections)
  dataSource.setCurrentSchema(ImageApiProperties.MetaSchema)

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
  lazy val migrationApiClient = new MigrationApiClient
  lazy val imageController = new ImageController
  lazy val imageControllerV2 = new ImageControllerV2
  lazy val rawController = new RawController
  lazy val internController = new InternController
  lazy val healthController = new HealthController
  lazy val resourcesApp = new ResourcesApp
  lazy val converterService = new ConverterService
  lazy val jestClient = JestClientFactory.getClient()
  lazy val searchConverterService = new SearchConverterService

  lazy val imageConverter = new ImageConverter
  lazy val authUser = new AuthUser
  lazy val authRole = new AuthRole
  lazy val clock = new SystemClock
}
