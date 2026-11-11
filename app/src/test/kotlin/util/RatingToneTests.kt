package org.jellyfin.androidtv.util

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class RatingToneTests : FunSpec({
	test("known ratings get their tone") {
		getRatingTone("G") shouldBe RatingTone.ALL
		getRatingTone("tv-y7") shouldBe RatingTone.ALL
		getRatingTone("PG-13") shouldBe RatingTone.TEEN
		getRatingTone("12A") shouldBe RatingTone.TEEN
		getRatingTone("TV-MA") shouldBe RatingTone.MATURE
	}

	test("ratings that are an age get a tone from the age") {
		getRatingTone("6") shouldBe RatingTone.ALL
		getRatingTone("FSK-12") shouldBe RatingTone.TEEN
		getRatingTone("16") shouldBe RatingTone.MATURE
		getRatingTone("18+") shouldBe RatingTone.MATURE
	}

	test("unknown or missing ratings get no tone") {
		getRatingTone(null) shouldBe null
		getRatingTone("") shouldBe null
		getRatingTone("Not Rated") shouldBe null
	}
})
