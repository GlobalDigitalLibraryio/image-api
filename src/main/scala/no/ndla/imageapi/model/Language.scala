/*
 * Part of NDLA image_api.
 * Copyright (C) 2016 NDLA
 *
 * See LICENSE
 */

package no.ndla.imageapi.model

import com.sksamuel.elastic4s.analyzers._
import io.digitallibrary.language.model.LanguageTag
import no.ndla.imageapi.model.domain.LanguageField
import no.ndla.mapping.ISO639

object Language {
  val DefaultLanguage = LanguageTag("en")
  val UnknownLanguage = LanguageTag("und")
  val AllLanguages = "all"
  val NoLanguage = ""

  val languageAnalyzers = Seq(
    LanguageAnalyzer(LanguageTag("nb"), NorwegianLanguageAnalyzer),
    LanguageAnalyzer(LanguageTag("en"), EnglishLanguageAnalyzer),
    LanguageAnalyzer(LanguageTag("fr"), FrenchLanguageAnalyzer),
    LanguageAnalyzer(LanguageTag("de"), GermanLanguageAnalyzer),
    LanguageAnalyzer(LanguageTag("es"), SpanishLanguageAnalyzer),
    LanguageAnalyzer(LanguageTag("zh"), ChineseLanguageAnalyzer),
    LanguageAnalyzer(UnknownLanguage, EnglishLanguageAnalyzer)
  )

  val supportedLanguages = languageAnalyzers.map(_.lang)

  def findByLanguageOrBestEffort[P <: LanguageField[_]](sequence: Seq[P], lang: LanguageTag): Option[P] = {
    def findFirstLanguageMatching(sequence: Seq[P], lang: Seq[LanguageTag]): Option[P] = {
      lang match {
        case Nil => sequence.headOption
        case head :: tail =>
          sequence.find(_.language == head) match {
            case Some(x) => Some(x)
            case None => findFirstLanguageMatching(sequence, tail)
          }
      }
    }

    findFirstLanguageMatching(sequence, Seq(lang, DefaultLanguage))
  }

  def languageOrUnknown(language: Option[String]): String = {
    language.filter(_.nonEmpty) match {
      case Some(x) => x
      case None => UnknownLanguage.toString
    }
  }

  def findSupportedLanguages[_](fields: Seq[LanguageField[_]]*): Seq[LanguageTag] = {
    val supportedLanguages = fields.flatMap(languageFields => languageFields.map(lf => lf.language)).distinct
    supportedLanguages.sortBy{lang =>
      ISO639.languagePriority.indexOf(lang.toString)
    }
  }
}

case class LanguageAnalyzer(lang: LanguageTag, analyzer: Analyzer)

