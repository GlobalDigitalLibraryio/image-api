package no.ndla.imageapi.controller

import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

import no.ndla.imageapi.TestData.{NdlaLogoGIFImage, NdlaLogoImage}
import no.ndla.imageapi.model.ImageNotFoundException
import no.ndla.imageapi.model.domain.RawImageQueryParameters
import no.ndla.imageapi.{ImageSwagger, TestData, TestEnvironment, UnitSuite}
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatra.test.scalatest.ScalatraSuite

import scala.util.{Failure, Success}

class RawControllerTest extends UnitSuite with ScalatraSuite with TestEnvironment {
  implicit val swagger = new ImageSwagger
  val imageName = "ndla_logo.jpg"
  val imageGifName = "ndla_logo.gif"

  override val imageConverter = new ImageConverter
  lazy val controller = new RawController
  addServlet(controller, "/*")

  val id = 1
  val idGif = 1

  override def beforeEach = {
    when(imageRepository.withId(id)).thenReturn(Some(TestData.bjorn))
    when(imageStorage.get(any[String])).thenReturn(Success(NdlaLogoImage))
  }

  test("That GET /image.jpg returns 200 if image was found") {
    get(s"/$imageName") {
      status should equal (200)

      val image = ImageIO.read(new ByteArrayInputStream(bodyBytes))
      image.getWidth should equal(189)
      image.getHeight should equal(60)
    }
  }

  test("That GET /image.jpg includes cache control headers if image was found") {
    get(s"/$imageName") {
      response.headers.get("Cache-Control") should equal (Some(Seq("public, max-age=31536000, immutable")))
    }
  }

  test("That GET /image.jpg returns 404 if image was not found") {
    when(imageStorage.get(any[String])).thenReturn(Failure(mock[ImageNotFoundException]))
    get(s"/$imageName") {
      status should equal (404)
    }
  }

  test("That GET /image.jpg with width resizing returns a resized image") {
    get(s"/$imageName?width=100") {
      status should equal (200)

      val image = ImageIO.read(new ByteArrayInputStream(bodyBytes))
      image.getWidth should equal(100)
    }
  }

  test("That GET /image.jpg with height resizing returns a resized image") {
    get(s"/$imageName?height=40") {
      status should equal (200)

      val image = ImageIO.read(new ByteArrayInputStream(bodyBytes))
      image.getHeight should equal(40)
    }
  }

  test("That GET /image.jpg with an invalid value for width returns 400") {
    get(s"/$imageName?width=twohundredandone") {
      status should equal (400)
    }
  }

  test("That GET /image.jpg with cropping returns a cropped image") {
    get(s"/$imageName?cropStartX=0&cropStartY=0&cropEndX=50&cropEndY=50") {
      status should equal (200)

      val image = ImageIO.read(new ByteArrayInputStream(bodyBytes))
      image.getWidth should equal(94)
      image.getHeight should equal(30)
    }
  }

  test("That GET /image.jpg with cropping and resizing returns a cropped and resized image") {
    get(s"/$imageName?cropStartX=0&cropStartY=0&cropEndX=50&cropEndY=50&width=50") {
      status should equal (200)

      val image = ImageIO.read(new ByteArrayInputStream(bodyBytes))
      image.getWidth should equal(50)
      image.getHeight should equal(16)
    }
  }

  test("GET /id/1 returns 200 if the image was found") {
    get(s"/id/$id") {
      status should equal (200)

      val image = ImageIO.read(new ByteArrayInputStream(bodyBytes))
      image.getWidth should equal(189)
      image.getHeight should equal(60)
    }
  }

  test("That GET /id/1 does not includes cache control headers for found image") {
    get(s"/id/$id") {
      response.headers.get("Cache-Control") should not be defined
    }
  }

  test("That GET /id/1 returns 404 if image was not found") {
    when(imageStorage.get(any[String])).thenReturn(Failure(mock[ImageNotFoundException]))

    get(s"/id/$id") {
      status should equal (404)
    }
  }

  test("That GET /id/1 with width resizing returns a resized image") {
    get(s"/id/$id?width=100") {
      status should equal (200)

      val image = ImageIO.read(new ByteArrayInputStream(bodyBytes))
      image.getWidth should equal(100)
    }
  }

  test("That GET /id/1 with height resizing returns a resized image") {
    get(s"/id/$id?height=40") {
      status should equal (200)

      val image = ImageIO.read(new ByteArrayInputStream(bodyBytes))
      image.getHeight should equal(40)
    }
  }

  test("That GET /id/1 with an invalid value for width returns 400") {
    get(s"/id/$id?width=twohundredandone") {
      status should equal (400)
    }
  }

  test("That GET /id/1 with cropping returns a cropped image") {
    get(s"/id/$id?cropStartX=0&cropStartY=0&cropEndX=50&cropEndY=50") {
      status should equal (200)

      val image = ImageIO.read(new ByteArrayInputStream(bodyBytes))
      image.getWidth should equal(94)
      image.getHeight should equal(30)
    }
  }

  test("That GET /id/1 with cropping and resizing returns a cropped and resized image") {
    get(s"/id/$id?cropStartX=0&cropStartY=0&cropEndX=50&cropEndY=50&width=50") {
      status should equal (200)

      val image = ImageIO.read(new ByteArrayInputStream(bodyBytes))
      image.getWidth should equal(50)
      image.getHeight should equal(16)
    }
  }

  test("That GET /id/1 with focal points and ratio returns a cropped image") {
    get(s"/id/$id?focalX=50&focalY=50&ratio=0.81") {
      status should equal (200)

      val image = ImageIO.read(new ByteArrayInputStream(bodyBytes))
      image.getWidth should equal(48)
      image.getHeight should equal(60)
    }
  }

  test("That GET /id/1 with focal points, ratio and width returns a cropped and resized image") {
    get(s"/id/$id?focalX=50&focalY=50&ratio=0.81&width=20") {
      status should equal (200)

      val image = ImageIO.read(new ByteArrayInputStream(bodyBytes))
      image.getWidth should equal(20)
      image.getHeight should equal(25)
    }
  }

  test("That GET /imageGif.gif with width resizing returns the original image") {
    when(imageStorage.get(any[String])).thenReturn(Success(NdlaLogoGIFImage))
    get(s"/$imageGifName?width=100") {
      status should equal (200)

      val image = ImageIO.read(new ByteArrayInputStream(bodyBytes))
      image.getWidth should equal(189)
      image.getHeight should equal(60)
    }
  }

  test("That GET /imageGif.gif with height resizing returns the original image") {
    when(imageStorage.get(any[String])).thenReturn(Success(NdlaLogoGIFImage))
    get(s"/$imageGifName?height=40") {
      status should equal (200)

      val image = ImageIO.read(new ByteArrayInputStream(bodyBytes))
      image.getWidth should equal(189)
      image.getHeight should equal(60)
    }
  }

  test("That GET /imageGif.gif with cropping returns the original image") {
    when(imageStorage.get(any[String])).thenReturn(Success(NdlaLogoGIFImage))
    get(s"/$imageGifName?cropStartX=0&cropStartY=0&cropEndX=50&cropEndY=50") {
      status should equal (200)

      val image = ImageIO.read(new ByteArrayInputStream(bodyBytes))
      image.getWidth should equal(189)
      image.getHeight should equal(60)
    }
  }

  test("That GET /imageGif.jpg with cropping and resizing returns the original image") {
    when(imageStorage.get(any[String])).thenReturn(Success(NdlaLogoGIFImage))
    get(s"/$imageGifName?cropStartX=0&cropStartY=0&cropEndX=50&cropEndY=50&width=50") {
      status should equal (200)

      val image = ImageIO.read(new ByteArrayInputStream(bodyBytes))
      image.getWidth should equal(189)
      image.getHeight should equal(60)
    }
  }
  test("That GET /id/1 with width resizing returns the original image") {
    when(imageStorage.get(any[String])).thenReturn(Success(NdlaLogoGIFImage))
    get(s"/id/$idGif?width=100") {
      status should equal (200)

      val image = ImageIO.read(new ByteArrayInputStream(bodyBytes))
      image.getWidth should equal(189)
      image.getHeight should equal(60)
    }
  }

  test("That GET /id/1 with height resizing returns the original image") {
    when(imageStorage.get(any[String])).thenReturn(Success(NdlaLogoGIFImage))
    get(s"/id/$idGif?height=40") {
      status should equal (200)

      val image = ImageIO.read(new ByteArrayInputStream(bodyBytes))
      image.getWidth should equal(189)
      image.getHeight should equal(60)
    }
  }

  test("That GET /id/2 with cropping returns the original image") {
    when(imageStorage.get(any[String])).thenReturn(Success(NdlaLogoGIFImage))
    get(s"/id/$idGif?cropStartX=0&cropStartY=0&cropEndX=50&cropEndY=50") {
      status should equal (200)

      val image = ImageIO.read(new ByteArrayInputStream(bodyBytes))
      image.getWidth should equal(189)
      image.getHeight should equal(60)
    }
  }

  test("That GET /id/1 with cropping and resizing returns the original image") {
    when(imageStorage.get(any[String])).thenReturn(Success(NdlaLogoGIFImage))
    get(s"/id/$idGif?cropStartX=0&cropStartY=0&cropEndX=50&cropEndY=50&width=50") {
      status should equal (200)

      val image = ImageIO.read(new ByteArrayInputStream(bodyBytes))
      image.getWidth should equal(189)
      image.getHeight should equal(60)
    }
  }

  test("That GET /123.jpg?storedRatio=0.81 redirects with stored parameters when they exist") {
    val returnParameters = RawImageQueryParameters(width = None, height = None, cropStartX = Some(50), cropStartY = Some(10), cropEndX = None, cropEndY = None, focalX = Some(50), focalY = Some(60), ratio = Some("0.81"))
    when(imageRepository.getCropParametersFor("/123.jpg", "0.81")).thenReturn(Some(returnParameters))
    get("/123.jpg?storedRatio=0.81") {
      status should equal (302)
      header.get("Location").map(_.endsWith("/123.jpg?cropStartX=50&focalX=50&ratio=0.81&cropStartY=10&focalY=60")) shouldBe Some(true)
    }
  }
  test("That GET /123.jpg?storedRatio=0.13 ignores parameter storedRatio when stored parameters doesn't exist for that ratio") {
    when(imageRepository.getCropParametersFor("/123.jpg", "0.13")).thenReturn(None)
    get("/123.jpg?storedRatio=0.13") {
      status should equal (200)
    }
  }
}
