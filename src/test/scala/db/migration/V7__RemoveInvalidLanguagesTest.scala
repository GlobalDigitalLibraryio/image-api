/*
 * Part of NDLA image_api.
 * Copyright (C) 2018 NDLA
 *
 * See LICENSE
 */

package db.migration

import no.ndla.imageapi.{TestEnvironment, UnitSuite}


class V7__RemoveInvalidLanguagesTest extends UnitSuite with TestEnvironment {

  val migration = new V7__RemoveInvalidLanguages

  test("add english language to stuff with unknown language") {
    val before = V7_ImageMetaInformation(
      Some(1),
      Seq(V7_ImageTitle("Tittel", Some("unknown")),
        V7_ImageTitle("Title", Some("unknown"))),
      Seq(V7_ImageAltText("Alttext", Some("unknown"))),
      "", 0, "", null,
      Seq(V7_ImageTag(Seq("Tag"), Some("unknown"))),
      Seq(V7_ImageCaption("Caption", Some("unknown"))), "", null)

    val after = migration.updateImageLanguage(before)
    after.titles.forall(_.language.get == "en") should be (true)
    after.alttexts.forall(_.language.get == "en") should be (true)
    after.tags.forall(_.language.get == "en") should be (true)
    after.captions.forall(_.language.get == "en") should be (true)
  }

  test("add english language to stuff with invalid language") {
    val before = V7_ImageMetaInformation(
      Some(1),
      Seq(V7_ImageTitle("Tittel", Some("x")),
        V7_ImageTitle("Title", Some("English"))),
      Seq(V7_ImageAltText("Alttext", Some("xx"))),
      "", 0, "", null,
      Seq(V7_ImageTag(Seq("Tag"), Some("english"))),
      Seq(V7_ImageCaption("Caption", Some("whatever"))), "", null)

    val after = migration.updateImageLanguage(before)
    after.titles.forall(_.language.get == "en") should be (true)
    after.alttexts.forall(_.language.get == "en") should be (true)
    after.tags.forall(_.language.get == "en") should be (true)
    after.captions.forall(_.language.get == "en") should be (true)
  }

  test("no changes are made to valid languages") {
    val before = V7_ImageMetaInformation(
      Some(1),
      Seq(V7_ImageTitle("Tittel", Some("en")),
        V7_ImageTitle("Title", Some("hi"))),
      Seq(V7_ImageAltText("Alttext", Some("bn"))),
      "", 0, "", null,
      Seq(V7_ImageTag(Seq("Tag"), Some("en"))),
      Seq(V7_ImageCaption("Caption", Some("am"))), "", null)

    migration.updateImageLanguage(before) should equal (before)
  }

}
