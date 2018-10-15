/*
 * Part of NDLA image_api.
 * Copyright (C) 2016 NDLA
 *
 * See LICENSE
 *
 */

package no.ndla.imageapi.integration

import com.amazonaws.AmazonServiceException
import com.amazonaws.services.s3.model.{GetObjectRequest, ObjectMetadata, S3Object}
import no.ndla.imageapi.TestData.NdlaLogoImage
import no.ndla.imageapi.{ImageApiProperties, TestData, TestEnvironment, UnitSuite}
import org.mockito.Matchers._
import org.mockito.Mockito._

import scala.util.{Failure, Success}

class ImageStorageServiceTest extends UnitSuite with TestEnvironment {

  val ImageStorageName = ImageApiProperties.StorageName
  val ImageWithNoThumb = TestData.nonexistingWithoutThumb
  val Content = "content"
  val ContentType = "image/jpeg"
  override val imageStorage = new AmazonImageStorageService

  override def beforeEach() = {
    reset(cloudinaryClient)
  }

  test("That objectExists returns true when image exists") {
    when(cloudinaryClient.doesObjectExist(any[String])).thenReturn(true)
    assert(imageStorage.objectExists("existingKey"))
  }

  test("That objectExists returns false when image does not exist") {
    when(cloudinaryClient.doesObjectExist(any[String])).thenReturn(false)
    assert(!imageStorage.objectExists("nonExistingKey"))
  }

  test("That get returns None when the key does not exist") {
    when(cloudinaryClient.getInformation(any[String])).thenReturn(Failure(new RuntimeException("Exception")))
    assert(imageStorage.get("nonexisting").isFailure)
  }

}
