/*
 * Part of NDLA image_api.
 * Copyright (C) 2017 NDLA
 *
 * See LICENSE
 */

package no.ndla.imageapi.model.api


import io.digitallibrary.language.model.LanguageTag
import no.ndla.imageapi.model.domain.StoredRawImageQueryParameters
import org.scalatra.swagger.annotations.{ApiModel, ApiModelProperty}

import scala.annotation.meta.field

@ApiModel(description = "Meta information for the image")
case class NewImageMetaInformationV2(@(ApiModelProperty@field)(description = "External ID") externalId: String,
                                     @(ApiModelProperty@field)(description = "Title for the image") title: String,
                                     @(ApiModelProperty@field)(description = "Alternative text for the image") alttext: String,
                                     @(ApiModelProperty@field)(description = "Describes the copyright information for the image") copyright: Copyright,
                                     @(ApiModelProperty@field)(description = "Searchable tags for the image") tags: Seq[String],
                                     @(ApiModelProperty@field)(description = "Caption for the image") caption: String,
                                     @(ApiModelProperty@field)(description = "BCP-47 code that represents the language used in the caption") language: LanguageTag,
                                     @(ApiModelProperty@field)(description = "Query parameters to store for a given aspect ratio") queryParameters: Option[StoredRawImageQueryParameters])

