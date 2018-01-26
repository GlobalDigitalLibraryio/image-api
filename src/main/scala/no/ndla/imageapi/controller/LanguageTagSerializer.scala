package no.ndla.imageapi.controller

import io.digitallibrary.language.model.LanguageTag
import org.json4s.CustomSerializer
import org.json4s.JsonAST.JString

class LanguageTagSerializer extends CustomSerializer[LanguageTag](_ => ( {
  case JString(s) => LanguageTag(s)
}, {
  case tag: LanguageTag => JString(tag.toString)
}))
