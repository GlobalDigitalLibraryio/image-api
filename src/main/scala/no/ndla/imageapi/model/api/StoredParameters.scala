package no.ndla.imageapi.model.api

import org.scalatra.swagger.annotations.{ApiModel, ApiModelProperty}

import scala.annotation.meta.field

@ApiModel(description = "Information about stored parameters for an image")
case class StoredParameters(@(ApiModelProperty@field)(description = "The URL of an image, as it's stored in the metadata") imageUrl: String,
                            @(ApiModelProperty@field)(description = "This, together with the imageUrl, is the lookup key") forRatio: String,
                            @(ApiModelProperty@field)(description = "Revision in the database") revision: Option[Int],
                            @(ApiModelProperty@field)(description = "The actual parameters stored for an image and a ratio") rawImageQueryParameters: RawImageQueryParameters)

@ApiModel(description = "Parameters used to crop and resize an image through the raw-endpoint")
case class RawImageQueryParameters(@(ApiModelProperty@field)(description = "Width of the image in pixels") width: Option[Int],
                                   @(ApiModelProperty@field)(description = "Height of the image in pixels") height: Option[Int],
                                   @(ApiModelProperty@field)(description = "Where to start cropping from the left side, in percent") cropStartX: Option[Int],
                                   @(ApiModelProperty@field)(description = "Where to start cropping from the top, in percent") cropStartY: Option[Int],
                                   @(ApiModelProperty@field)(description = "Where to stop cropping from the left side, in percent") cropEndX: Option[Int],
                                   @(ApiModelProperty@field)(description = "Where to end cropping from the top, in percent") cropEndY: Option[Int],
                                   @(ApiModelProperty@field)(description = "Where a focal point is located from the left side, in percent") focalX: Option[Int],
                                   @(ApiModelProperty@field)(description = "Where a focal point is located from the top, in percent") focalY: Option[Int],
                                   @(ApiModelProperty@field)(description = "Wanted ratio for the image") ratio: Option[String])
