/*
 * Part of NDLA image_api.
 * Copyright (C) 2016 NDLA
 *
 * See LICENSE
 *
 */

package no.ndla.imageapi.service

import com.netaporter.uri.Uri.parse
import com.typesafe.scalalogging.LazyLogging
import io.digitallibrary.language.model.LanguageTag
import io.digitallibrary.network.ApplicationUrl
import no.ndla.imageapi.ImageApiProperties
import no.ndla.imageapi.auth.User
import no.ndla.imageapi.model.Language._
import no.ndla.imageapi.model.{api, domain}

trait ConverterService {
  this: User with Clock =>
  val converterService: ConverterService

  class ConverterService extends LazyLogging {


    def asApiAuthor(domainAuthor: domain.Author): api.Author = {
      api.Author(domainAuthor.`type`, domainAuthor.name)
    }

    def asApiCopyright(domainCopyright: domain.Copyright): api.Copyright = {
      api.Copyright(
        asApiLicense(domainCopyright.license),
        domainCopyright.origin,
        domainCopyright.creators.map(asApiAuthor),
        domainCopyright.processors.map(asApiAuthor),
        domainCopyright.rightsholders.map(asApiAuthor),
        domainCopyright.agreementId,
        domainCopyright.validFrom,
        domainCopyright.validTo)
    }

    def asApiImage(domainImage: domain.Image, baseUrl: Option[String] = None): api.Image = {
      api.Image(baseUrl.getOrElse("") + domainImage.fileName, domainImage.size, domainImage.contentType)
    }

    def asApiImageAltText(domainImageAltText: domain.ImageAltText): api.ImageAltText = {
      api.ImageAltText(domainImageAltText.alttext, domainImageAltText.language)
    }

    def asApiImageMetaInformationWithApplicationUrl(domainImageMetaInformation: domain.ImageMetaInformation): api.ImageMetaInformation = {
      asApiImageMetaInformation(domainImageMetaInformation, Some(ApplicationUrl.get))
    }

    def asApiImageMetaInformationWithDomainUrl(domainImageMetaInformation: domain.ImageMetaInformation): api.ImageMetaInformation = {
      asApiImageMetaInformation(domainImageMetaInformation, Some(ImageApiProperties.ImageApiUrlBase))
    }

    private def asApiImageMetaInformation(domainImageMetaInformation: domain.ImageMetaInformation, apiBaseUrl: Option[String] = None): api.ImageMetaInformation = {
      api.ImageMetaInformation(
        domainImageMetaInformation.id.get.toString,
        apiBaseUrl.getOrElse("") + domainImageMetaInformation.id.get,
        domainImageMetaInformation.titles.map(asApiImageTitle),
        domainImageMetaInformation.alttexts.map(asApiImageAltText),
        asApiUrl(domainImageMetaInformation.imageUrl),
        domainImageMetaInformation.size,
        domainImageMetaInformation.contentType,
        asApiCopyright(domainImageMetaInformation.copyright),
        domainImageMetaInformation.tags.map(asApiImageTag),
        domainImageMetaInformation.captions.map(asApiCaption))
    }

    def asNewImageMetaInformation(newImageMeta: api.NewImageMetaInformationV2): api.NewImageMetaInformationV2 = {
      api.NewImageMetaInformationV2(
        newImageMeta.title,
        newImageMeta.alttext,
        newImageMeta.copyright,
        newImageMeta.tags,
        newImageMeta.caption,
        newImageMeta.language
      )
    }

    def asApiImageMetaInformationWithApplicationUrlAndSingleLanguage(domainImageMetaInformation: domain.ImageMetaInformation, language: LanguageTag): Option[api.ImageMetaInformationV2] = {
      asImageMetaInformationV2(domainImageMetaInformation, language, ApplicationUrl.get)
    }

    def asApiImageMetaInformationWithDomainUrlAndSingleLanguage(domainImageMetaInformation: domain.ImageMetaInformation, language: LanguageTag): Option[api.ImageMetaInformationV2] = {
      asImageMetaInformationV2(domainImageMetaInformation, language, ImageApiProperties.ImageApiUrlBase.replace("v1", "v2"))
    }

    private def asImageMetaInformationV2(imageMeta: domain.ImageMetaInformation, language: LanguageTag, baseUrl: String): Option[api.ImageMetaInformationV2] = {
      val defaultLanguage = DefaultLanguage
      val title = findByLanguageOrBestEffort(imageMeta.titles, language).map(asApiImageTitle).getOrElse(api.ImageTitle("", defaultLanguage))
      val alttext = findByLanguageOrBestEffort(imageMeta.alttexts, language).map(asApiImageAltText).getOrElse(api.ImageAltText("", defaultLanguage))
      val tags = findByLanguageOrBestEffort(imageMeta.tags, language).map(asApiImageTag).getOrElse(api.ImageTag(Seq(), defaultLanguage))
      val caption = findByLanguageOrBestEffort(imageMeta.captions, language).map(asApiCaption).getOrElse(api.ImageCaption("", defaultLanguage))

      Some(api.ImageMetaInformationV2(
        imageMeta.id.get.toString,
        baseUrl + imageMeta.id.get,
        title,
        alttext,
        asApiUrl(imageMeta.imageUrl),
        imageMeta.size,
        imageMeta.contentType,
        withAgreementCopyright(asApiCopyright(imageMeta.copyright)),
        tags,
        caption,
        getSupportedLanguages(imageMeta)))
    }

    def withAgreementCopyright(image: domain.ImageMetaInformation): domain.ImageMetaInformation = {
      val agreementCopyright = image.copyright

      image.copy(copyright = image.copyright.copy(
        license = agreementCopyright.license,
        creators = agreementCopyright.creators,
        rightsholders = agreementCopyright.rightsholders,
        validFrom = agreementCopyright.validFrom,
        validTo = agreementCopyright.validTo
      ))
    }

    def withAgreementCopyright(copyright: api.Copyright): api.Copyright = {
      val agreementCopyright = copyright
      copyright.copy(
        license = agreementCopyright.license,
        creators = agreementCopyright.creators,
        rightsholders = agreementCopyright.rightsholders,
        validFrom = agreementCopyright.validFrom,
        validTo = agreementCopyright.validTo
      )
    }

    def asApiImageTag(domainImageTag: domain.ImageTag): api.ImageTag = {
      api.ImageTag(domainImageTag.tags, domainImageTag.language)
    }

    def asApiCaption(domainImageCaption: domain.ImageCaption): api.ImageCaption =
      api.ImageCaption(domainImageCaption.caption, domainImageCaption.language)

    def asApiImageTitle(domainImageTitle: domain.ImageTitle): api.ImageTitle = {
      api.ImageTitle(domainImageTitle.title, domainImageTitle.language)
    }

    def asApiLicense(domainLicense: domain.License): api.License = {
      api.License(domainLicense.license, domainLicense.description, domainLicense.url)
    }

    def asApiUrl(url: String): String = {
      ImageApiProperties.CloudFrontUrl + (if (url.startsWith("/")) url else "/" + url)
    }

    def asDomainImageMetaInformationV2(imageMeta: api.NewImageMetaInformationV2, image: domain.Image): domain.ImageMetaInformation = {
      domain.ImageMetaInformation(
        None,
        Seq(asDomainTitle(imageMeta.title, imageMeta.language)),
        Seq(asDomainAltText(imageMeta.alttext, imageMeta.language)),
        parse(image.fileName).toString,
        image.size,
        image.contentType,
        toDomainCopyright(imageMeta.copyright),
        if (imageMeta.tags.nonEmpty) Seq(toDomainTag(imageMeta.tags, imageMeta.language)) else Seq.empty,
        Seq(domain.ImageCaption(imageMeta.caption, imageMeta.language)),
        authUser.userOrClientid(),
        clock.now()
      )
    }

    def asDomainTitle(title: String, language: LanguageTag): domain.ImageTitle = {
      domain.ImageTitle(title, language)
    }

    def asDomainAltText(alt: String, language: LanguageTag): domain.ImageAltText = {
      domain.ImageAltText(alt, language)
    }

    def toDomainCopyright(copyright: api.Copyright): domain.Copyright = {
      domain.Copyright(
        toDomainLicense(copyright.license),
        copyright.origin,
        copyright.creators.map(toDomainAuthor),
        copyright.processors.map(toDomainAuthor),
        copyright.rightsholders.map(toDomainAuthor),
        copyright.agreementId,
        copyright.validFrom,
        copyright.validTo)
    }

    def toDomainLicense(license: api.License): domain.License = {
      domain.License(license.license, license.description, license.url)
    }

    def toDomainAuthor(author: api.Author): domain.Author = {
      domain.Author(author.`type`, author.name)
    }

    def toDomainTag(tags: Seq[String], language: LanguageTag): domain.ImageTag = {
      domain.ImageTag(tags, language)
    }

    def toDomainCaption(caption: String, language: LanguageTag): domain.ImageCaption = {
      domain.ImageCaption(caption, language)
    }

    def getSupportedLanguages(domainImageMetaInformation: domain.ImageMetaInformation): Seq[LanguageTag] = {
      domainImageMetaInformation.titles.map(_.language)
        .++:(domainImageMetaInformation.alttexts.map(_.language))
        .++:(domainImageMetaInformation.tags.map(_.language))
        .++:(domainImageMetaInformation.captions.map(_.language))
        .distinct
    }

  }

}
