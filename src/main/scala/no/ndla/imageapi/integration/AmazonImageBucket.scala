package no.ndla.imageapi.integration

import java.io.{File, InputStream}

import com.amazonaws.AmazonServiceException
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model._
import model.ImageMetaInformation
import no.ndla.imageapi.business.ImageBucket

class AmazonImageBucket(imageBucketName: String, s3Client: AmazonS3Client) extends ImageBucket {

  def get(imageKey: String): Option[(String, InputStream)] = {
    try{
      val s3Object = s3Client.getObject(new GetObjectRequest(imageBucketName, imageKey))
      Option(
        s3Object.getObjectMetadata().getContentType,
        s3Object.getObjectContent)
    } catch {
      case ase: Exception => None
    }
  }

  def upload(imageMetaInformation: ImageMetaInformation, imageDirectory: String) = {
    if(Option(imageMetaInformation.images.small).isDefined){
      val thumbRequest: PutObjectRequest = new PutObjectRequest(imageBucketName, imageMetaInformation.images.small.url, new File(imageDirectory + imageMetaInformation.images.small.url))
      val thumbResult = s3Client.putObject(thumbRequest)
    }

    val fullRequest: PutObjectRequest = new PutObjectRequest(imageBucketName, imageMetaInformation.images.full.url, new File(imageDirectory + imageMetaInformation.images.full.url))
    s3Client.putObject(fullRequest)
  }

  def contains(imageMetaInformation: ImageMetaInformation): Boolean = {
    try {
      Option(s3Client.getObject(new GetObjectRequest(imageBucketName, imageMetaInformation.images.full.url))).isDefined
    } catch {
      case ase: AmazonServiceException => if (ase.getErrorCode == "NoSuchKey") false else throw ase
    }
  }

  def create() = {
    s3Client.createBucket(new CreateBucketRequest(imageBucketName))
  }

  def exists(): Boolean = {
    s3Client.doesBucketExist(imageBucketName)
  }
}
