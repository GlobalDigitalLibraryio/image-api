/*
 * Part of NDLA image_api.
 * Copyright (C) 2016 NDLA
 *
 * See LICENSE
 */

package no.ndla.imageapi.model.search

import io.digitallibrary.language.model.LanguageTag
import no.ndla.imageapi.controller.LanguageTagSerializer
import org.json4s.JsonAST.{JArray, JField, JObject, JString}
import org.json4s.{CustomSerializer, MappingException}


class SearchableLanguageValueSerializer extends CustomSerializer[SearchableLanguageValues](format => ( {
  case JObject(items) => SearchableLanguageValues(items.map {
    case JField(name, JString(value)) => LanguageValue(LanguageTag(name), value)
  })
}, {
  case x: SearchableLanguageValues =>
    JObject(x.languageValues.map(languageValue => JField(languageValue.language.toString, JString(languageValue.value))).toList)
}))


class SearchableLanguageListSerializer extends CustomSerializer[SearchableLanguageList](format => ( {
  case JObject(items) => {
    SearchableLanguageList(items.map {
      case JField(name, JArray(fieldItems)) => LanguageValue(LanguageTag(name), fieldItems.map {
        case JString(value) => value
        case x => throw new MappingException(s"Cannot convert $x to SearchableLanguageList")
      }.to[Seq])
    })
  }
}, {
  case x: SearchableLanguageList =>
    JObject(x.languageValues.map(languageValue => JField(languageValue.language.toString, JArray(languageValue.value.map(lv => JString(lv)).toList))).toList)
}))

object SearchableLanguageFormats {
  val JSonFormats = org.json4s.DefaultFormats + new SearchableLanguageValueSerializer + new SearchableLanguageListSerializer + new LanguageTagSerializer
}
