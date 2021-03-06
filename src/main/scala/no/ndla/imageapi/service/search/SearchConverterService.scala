/*
 * Part of NDLA image_api.
 * Copyright (C) 2016 NDLA
 *
 * See LICENSE
 */

package no.ndla.imageapi.service.search

import com.netaporter.uri.Uri.parse
import com.sksamuel.elastic4s.http.search.SearchHit
import com.typesafe.scalalogging.LazyLogging
import io.digitallibrary.language.model.LanguageTag
import io.digitallibrary.network.ApplicationUrl
import no.ndla.imageapi.ImageApiProperties
import no.ndla.imageapi.ImageApiProperties.DefaultLanguage
import no.ndla.imageapi.model.Language
import no.ndla.imageapi.model.api.{ImageAltText, ImageMetaSummary, ImageTitle}
import no.ndla.imageapi.model.domain.{ImageMetaInformation, StorageService}
import no.ndla.imageapi.model.search.{LanguageValue, SearchableImage, SearchableLanguageList, SearchableLanguageValues}
import no.ndla.imageapi.service.ConverterService

trait SearchConverterService {
  this: ConverterService =>
  val searchConverterService: SearchConverterService

  class SearchConverterService extends LazyLogging {
    def asSearchableImage(image: ImageMetaInformation): SearchableImage = {
      val imageWithAgreement = converterService.withAgreementCopyright(image)

      val defaultTitle = imageWithAgreement.titles.sortBy(title => {
        val languagePriority = Language.languageAnalyzers.map(la => la.lang.toString()).reverse
        languagePriority.indexOf(title.language)
      }).lastOption

      SearchableImage(
        id = imageWithAgreement.id.get,
        titles = SearchableLanguageValues(imageWithAgreement.titles.map(title => LanguageValue(title.language, title.title))),
        alttexts = SearchableLanguageValues(imageWithAgreement.alttexts.map(alttext => LanguageValue(alttext.language, alttext.alttext))),
        captions = SearchableLanguageValues(imageWithAgreement.captions.map(caption => LanguageValue(caption.language, caption.caption))),
        tags = SearchableLanguageList(imageWithAgreement.tags.map(tag => LanguageValue(tag.language, tag.tags))),
        contributors = image.copyright.creators.map(c => c.name) ++ image.copyright.processors.map(p => p.name) ++ image.copyright.rightsholders.map(r => r.name),
        license = imageWithAgreement.copyright.license.toString,
        imageSize = imageWithAgreement.size,
        previewUrl = parse(imageWithAgreement.imageUrl).toString,
        lastUpdated = imageWithAgreement.updated,
        defaultTitle = defaultTitle.map(t => t.title),
        storageService = imageWithAgreement.storageService.map(_.toString)
      )
    }

    def asImageMetaSummary(searchableImage: SearchableImage, language: Option[LanguageTag]): ImageMetaSummary = {
      val title = searchableImage.titles.languageValues.find(title => title.language == language.getOrElse(DefaultLanguage))
        .orElse(searchableImage.titles.languageValues.headOption)
        .map(res => ImageTitle(res.value, res.language))
        .getOrElse(ImageTitle("", DefaultLanguage))
      val altText = Language.findByLanguageOrBestEffort(searchableImage.alttexts.languageValues, language.get)
        .map(res => ImageAltText(res.value, res.language))
        .getOrElse(ImageAltText("", DefaultLanguage))

      val supportedLanguages = Language.findSupportedLanguages(
        searchableImage.titles.languageValues,
        searchableImage.alttexts.languageValues,
        searchableImage.captions.languageValues,
        searchableImage.tags.languageValues
      )

      val previewUrl = StorageService.valueOf(searchableImage.storageService.getOrElse("AWS")) match {
        case StorageService.CLOUDINARY => ImageApiProperties.CloudinaryUrl + searchableImage.previewUrl
        case StorageService.AWS => ImageApiProperties.CloudFrontUrl + searchableImage.previewUrl
      }

      ImageMetaSummary(
        id = searchableImage.id.toString,
        title = title,
        contributors = searchableImage.contributors,
        altText = altText,
        previewUrl = previewUrl,
        metaUrl = ApplicationUrl.get + searchableImage.id,
        license = searchableImage.license,
        supportedLanguages = supportedLanguages
      )
    }

    def getLanguageFromHit(result: SearchHit): Option[String] = {
      val sortedInnerHits = result.innerHits.toList.filter(ih => ih._2.total > 0).sortBy {
        case (_, hit) => hit.max_score
      }.reverse

      val matchLanguage = sortedInnerHits.headOption.flatMap {
        case (_, innerHit) =>
          innerHit.hits.sortBy(hit => hit.score).reverse.headOption.flatMap(hit => {
            hit.highlight.headOption.map(hl => hl._1.split('.').last)
          })
      }

      matchLanguage match {
        case Some(lang) =>
          Some(lang)
        case _ =>
          val titles = result.sourceAsMap.get("titles")
          val titleMap = titles.map(tm => {
            tm.asInstanceOf[Map[String, _]]
          })

          val languages = titleMap.map(title => title.keySet.toList)

          languages.flatMap(languageList => {
            languageList.sortBy(lang => {
              val languagePriority = Language.languageAnalyzers.map(la => la.lang).reverse
              languagePriority.indexOf(lang)
            }).lastOption
          })
      }

    }

  }

}
