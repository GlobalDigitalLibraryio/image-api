package no.ndla.imageapi.model.api

import io.digitallibrary.language.model.LanguageTag
import org.scalatra.swagger.annotations.{ApiModel, ApiModelProperty}

import scala.annotation.meta.field

@ApiModel(description = "An image caption")
case class ImageCaption(@(ApiModelProperty@field)(description = "The caption for the image") caption: String,
                        @(ApiModelProperty@field)(description = "BCP-47 code that represents the language used in the caption") language: LanguageTag)
