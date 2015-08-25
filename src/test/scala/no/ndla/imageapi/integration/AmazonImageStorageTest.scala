package no.ndla.imageapi.integration

import java.io.ByteArrayInputStream

import com.amazonaws.AmazonServiceException
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.{GetObjectRequest, ObjectMetadata, PutObjectRequest, S3Object}
import no.ndla.imageapi.model.Image
import no.ndla.imageapi.{TestData, UnitSpec}
import no.ndla.imageapi.business.ImageStorage
import org.mockito.Matchers._
import org.mockito.Mockito._

class AmazonImageStorageTest extends UnitSpec{

  val ImageStorageName = "TestBucket"
  val ImageWithNoThumb = TestData.nonexistingWithoutThumb
  val ImageWithThumb = TestData.nonexisting
  val Content = "content"
  val ContentType = "image/jpeg"

  var imageStorage: ImageStorage = _
  var s3ClientMock: AmazonS3Client = _

  override def beforeEach() = {
    s3ClientMock = mock[AmazonS3Client]
    imageStorage = new AmazonImageStorage(ImageStorageName, s3ClientMock)
  }

  "AmazonImageStorage.exists" should "return true when bucket exists" in {
    when(s3ClientMock.doesBucketExist(ImageStorageName)).thenReturn(true)
    assert(imageStorage.exists())
  }

  it should "return false when bucket does not exist" in {
    when(s3ClientMock.doesBucketExist(ImageStorageName)).thenReturn(false)
    assert(imageStorage.exists() == false)
  }

  "AmazonImageStorage.contains" should "return true when image exists" in {
    val s3ObjectMock = mock[S3Object]
    when(s3ClientMock.getObject(any[GetObjectRequest])).thenReturn(s3ObjectMock)
    assert(imageStorage.contains("existingKey"))
  }

  it should "return false when image does not exist" in {
    val ase = new AmazonServiceException("Exception")
    ase.setErrorCode("NoSuchKey")
    when(s3ClientMock.getObject(any[GetObjectRequest])).thenThrow(ase)
    assert(imageStorage.contains("nonExistingKey") == false)
  }

  "AmazonImageStorage.get" should "return a tuple with contenttype and data when the key exists" in {
    val s3object = new S3Object()
    s3object.setObjectMetadata(new ObjectMetadata())
    s3object.getObjectMetadata().setContentType(ContentType)
    s3object.setObjectContent(new ByteArrayInputStream(Content.getBytes()))
    when(s3ClientMock.getObject(any[GetObjectRequest])).thenReturn(s3object)

    val image = imageStorage.get("existing")
    assert(image.isDefined)
    assert(image.get._1 == ContentType)
    assert(scala.io.Source.fromInputStream(image.get._2).mkString == Content)
  }

  it should "return None when the key does not exist" in {
    when(s3ClientMock.getObject(any[GetObjectRequest])).thenThrow(new RuntimeException("Exception"))
    assert(imageStorage.get("nonexisting").isEmpty)
  }

  "AmazonImageStorage.upload" should "upload both thumb and image when both defined" in {
    imageStorage.upload(ImageWithThumb, "test")
    verify(s3ClientMock, times(2)).putObject(any[PutObjectRequest])
  }

  it should "upload only image when thumb is not defined" in {
    imageStorage.upload(ImageWithNoThumb, "test")
    verify(s3ClientMock, times(1)).putObject(any[PutObjectRequest])
  }

}
