package no.ndla.imageapi.model.api

import org.scalatra.swagger.annotations.ApiModel
import org.scalatra.swagger.runtime.annotations.ApiModelProperty

import scala.annotation.meta.field

@ApiModel(description = "Information about the url and alttext of an image")
case class ImageUrl(@(ApiModelProperty@field)(description = "Image id of the image") id: String,
                    @(ApiModelProperty@field)(description = "URL to the image") url: String,
                    @(ApiModelProperty@field)(description = "Optional alt-text of the image") alttext: Option[String])
