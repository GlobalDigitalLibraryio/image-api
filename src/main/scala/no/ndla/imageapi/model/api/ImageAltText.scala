package no.ndla.imageapi.model.api

import io.digitallibrary.language.model.LanguageTag
import org.scalatra.swagger.annotations.{ApiModel, ApiModelProperty}

import scala.annotation.meta.field

@ApiModel(description = "Alt-text of an image")
case class ImageAltText(@(ApiModelProperty@field)(description = "The alternative text for the image") alttext: String,
                        @(ApiModelProperty@field)(description = "BCP-47 code that represents the language used in the alternative text") language: LanguageTag)
