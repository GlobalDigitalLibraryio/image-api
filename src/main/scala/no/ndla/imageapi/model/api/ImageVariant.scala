package no.ndla.imageapi.model.api

case class ImageVariant(ratio: String, revision: Option[Int], topLeftX: Int, topLeftY: Int, width: Int, height: Int)
