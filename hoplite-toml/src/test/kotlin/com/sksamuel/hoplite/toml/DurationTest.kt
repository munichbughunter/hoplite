package com.sksamuel.hoplite.toml

import com.sksamuel.hoplite.ConfigLoader
import io.kotest.matchers.shouldBe
import io.kotest.core.spec.style.StringSpec
import java.time.Duration

class DurationTest : StringSpec() {
  init {
    "durations should be parsable from longs" {
      data class Test(val a: Duration)
      ConfigLoader().loadConfigOrThrow<Test>("/duration_as_longs.toml").a shouldBe Duration.ofMillis(123123213123)
    }
  }
}
