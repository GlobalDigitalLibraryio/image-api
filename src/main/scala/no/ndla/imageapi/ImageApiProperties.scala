/*
 * Part of NDLA image_api.
 * Copyright (C) 2016 NDLA
 *
 * See LICENSE
 *
 */

package no.ndla.imageapi

import com.typesafe.scalalogging.LazyLogging
import no.ndla.network.secrets.PropertyKeys
import no.ndla.network.secrets.Secrets.readSecrets

import scala.collection.mutable
import scala.io.Source
import scala.util.{Properties, Success, Try}


object ImageApiProperties extends LazyLogging {

  var ImageApiProps: mutable.Map[String, Option[String]] = mutable.HashMap()

  lazy val ApplicationPort = 80
  lazy val ContactEmail = "christergundersen@ndla.no"

  lazy val Domain = get("DOMAIN")
  lazy val ImageUrlBase = Domain + ImageControllerPath + "/"

  lazy val MetaUserName = get(PropertyKeys.MetaUserNameKey)
  lazy val MetaPassword = get(PropertyKeys.MetaPasswordKey)
  lazy val MetaResource = get(PropertyKeys.MetaResourceKey)
  lazy val MetaServer = get(PropertyKeys.MetaServerKey)
  lazy val MetaPort = getInt(PropertyKeys.MetaPortKey)
  lazy val MetaSchema = get(PropertyKeys.MetaSchemaKey)
  lazy val MetaInitialConnections = 3
  lazy val MetaMaxConnections = 20

  lazy val StorageName = get("NDLA_ENVIRONMENT") + ".images.ndla"

  lazy val SearchServer = getOrElse("SEARCH_SERVER", "http://search-image-api.ndla-local")
  lazy val SearchRegion = getOrElse("SEARCH_REGION", "eu-central-1")
  lazy val SearchIndex = "images"
  lazy val SearchDocument = "image"
  lazy val DefaultPageSize: Int = 10
  lazy val MaxPageSize: Int = 100
  lazy val IndexBulkSize = 1000
  lazy val RunWithSignedSearchRequests = getOrElse("RUN_WITH_SIGNED_SEARCH_REQUESTS", "true").toBoolean

  lazy val TopicAPIUrl = get("TOPIC_API_URL")
  lazy val MigrationHost = get("MIGRATION_HOST")
  lazy val MigrationUser = get("MIGRATION_USER")
  lazy val MigrationPassword = get("MIGRATION_PASSWORD")

  val CorrelationIdKey = "correlationID"
  val CorrelationIdHeader = "X-Correlation-ID"
  val HealthControllerPath = "/health"
  val ImageControllerPath = "/images"
  val MappingHost = "mapping-api.ndla-local"
  val IsoMappingCacheAgeInMs = 1000 * 60 * 60 // 1 hour caching
  val LicenseMappingCacheAgeInMs = 1000 * 60 * 60 // 1 hour caching

  def setProperties(properties: Map[String, Option[String]]) = {
    Success(properties.foreach(prop => ImageApiProps.put(prop._1, prop._2)))
  }

  private def getOrElse(envKey: String, defaultValue: String) = {
    ImageApiProps.get(envKey).flatten match {
      case Some(value) => value
      case None => defaultValue
    }
  }

  private def get(envKey: String): String = {
    ImageApiProps.get(envKey).flatten match {
      case Some(value) => value
      case None => throw new NoSuchFieldError(s"Missing environment variable $envKey")
    }
  }

  private def getInt(envKey: String):Integer = {
    get(envKey).toInt
  }

  private def getBoolean(envKey: String): Boolean = {
    get(envKey).toBoolean
  }
}

object PropertiesLoader extends LazyLogging {
  val EnvironmentFile = "/image-api.env"

  def readPropertyFile() = {
    Try(Source.fromInputStream(getClass.getResourceAsStream(EnvironmentFile)).getLines().map(key => key -> Properties.envOrNone(key)).toMap)
  }

  def load() = {
    val verification = for {
      file <- readPropertyFile()
      secrets <- readSecrets("image_api.secrets")
      didSetProperties <- ImageApiProperties.setProperties(file ++ secrets)
    } yield didSetProperties

    if(verification.isFailure){
      logger.error("Unable to load properties", verification.failed.get)
      System.exit(1)
    }
  }
}
