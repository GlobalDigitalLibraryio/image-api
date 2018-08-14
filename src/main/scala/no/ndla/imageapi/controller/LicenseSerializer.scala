package no.ndla.imageapi.controller

import io.digitallibrary.license.model.License
import org.json4s.CustomSerializer
import org.json4s.JsonAST.JString

class LicenseSerializer extends CustomSerializer[License](_ => ( {
  case JString(s) => License(s)
}, {
  case license: License => JString(license.toString)
}))
