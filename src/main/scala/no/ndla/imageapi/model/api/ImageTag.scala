package no.ndla.imageapi.model.api

import io.digitallibrary.language.model.LanguageTag
import org.scalatra.swagger.annotations.{ApiModel, ApiModelProperty}

import scala.annotation.meta.field

@ApiModel(description = "An tag for an image")
case class ImageTag(@(ApiModelProperty@field)(description = "The searchable tag.") tags: Seq[String],
                    @(ApiModelProperty@field)(description = "BCP-47 code that represents the language used in tag") language: LanguageTag)
