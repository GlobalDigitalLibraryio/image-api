package no.ndla.imageapi.model.domain

import io.digitallibrary.language.model.LanguageTag


trait LanguageField[T] {
  def value: T
  def language: LanguageTag
}
