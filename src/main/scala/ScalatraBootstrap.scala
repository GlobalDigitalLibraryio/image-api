import javax.servlet.ServletContext

import no.ndla.imageapi.{ImageController, ImageSwagger, ResourcesApp}
import org.scalatra.LifeCycle

class ScalatraBootstrap extends LifeCycle {

  implicit val swagger = new ImageSwagger

  override def init(context: ServletContext) {
    // Mount servlets.
    context.mount(new ImageController, "/images", "images")
    context.mount(new ResourcesApp, "/api-docs")
  }

}
