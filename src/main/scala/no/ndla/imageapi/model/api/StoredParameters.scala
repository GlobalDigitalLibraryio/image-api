package no.ndla.imageapi.model.api

import org.scalatra.swagger.annotations.{ApiModel, ApiModelProperty}

import scala.annotation.meta.field

// TODO Fyll ut
@ApiModel(description = "")
case class StoredParameters(@(ApiModelProperty@field)(description = "") imageUrl: String,
                            @(ApiModelProperty@field)(description = "") forRatio: String,
                            @(ApiModelProperty@field)(description = "") revision: Option[Int],
                            @(ApiModelProperty@field)(description = "") rawImageQueryParameters: RawImageQueryParameters)

@ApiModel(description = "")
case class RawImageQueryParameters(@(ApiModelProperty@field)(description = "") width: Option[Int],
                                   @(ApiModelProperty@field)(description = "") height: Option[Int],
                                   @(ApiModelProperty@field)(description = "") cropStartX: Option[Int],
                                   @(ApiModelProperty@field)(description = "") cropStartY: Option[Int],
                                   @(ApiModelProperty@field)(description = "") cropEndX: Option[Int],
                                   @(ApiModelProperty@field)(description = "") cropEndY: Option[Int],
                                   @(ApiModelProperty@field)(description = "") focalX: Option[Int],
                                   @(ApiModelProperty@field)(description = "") focalY: Option[Int],
                                   @(ApiModelProperty@field)(description = "") ratio: Option[String])
