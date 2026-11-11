package org.jellyfin.androidtv.util

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.jellyfin.androidtv.preference.constant.WatchProgressChip

private const val MINUTE = 600_000_000L

class WatchProgressTests : FunSpec({
	test("formats ticks as minutes and seconds or hours") {
		formatTicks(0) shouldBe "0:00"
		formatTicks(MINUTE * 2 + 30 * 10_000_000L) shouldBe "2:30"
		formatTicks(MINUTE * 62 + 3 * 10_000_000L) shouldBe "1:02:03"
	}

	test("each mode shows its own text") {
		formatWatchProgress(WatchProgressChip.PERCENT, MINUTE * 30, MINUTE * 120) shouldBe "25%"
		formatWatchProgress(WatchProgressChip.WATCHED, MINUTE * 30, MINUTE * 120) shouldBe "30:00"
		formatWatchProgress(WatchProgressChip.REMAINING, MINUTE * 30, MINUTE * 120) shouldBe "1:30:00"
	}

	test("nothing is shown when it is off or not started") {
		formatWatchProgress(WatchProgressChip.OFF, MINUTE, MINUTE * 10) shouldBe null
		formatWatchProgress(WatchProgressChip.PERCENT, 0, MINUTE * 10) shouldBe null
		formatWatchProgress(WatchProgressChip.PERCENT, null, MINUTE * 10) shouldBe null
		formatWatchProgress(WatchProgressChip.PERCENT, MINUTE, null) shouldBe null
	}

	test("a position past the end counts as finished") {
		formatWatchProgress(WatchProgressChip.PERCENT, MINUTE * 20, MINUTE * 10) shouldBe "100%"
	}
})
