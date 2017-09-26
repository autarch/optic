package utils

import org.scalatest.FunSpec

class StringBuilderImplicitsTest extends FunSpec {
  describe("String builder implicits") {
    import com.opticdev.core.utils.StringBuilderImplicits._

    it("can update with contents of same length") {
      val exampleBuilder: StringBuilder = new StringBuilder("Hello World")
      exampleBuilder.updateRange(Range(0,1), "A")
      assert(exampleBuilder.toString() == "Aello World")
    }

    it("can update with contents of a longer length") {
      val exampleBuilder: StringBuilder = new StringBuilder("Hello World")
      exampleBuilder.updateRange(Range(0,5), "Goodbye")
      assert(exampleBuilder.toString() == "Goodbye World")
    }

    it("can update with contents of a shorter length") {
      val exampleBuilder: StringBuilder = new StringBuilder("Hello World")
      exampleBuilder.updateRange(Range(0,5), "THE")
      assert(exampleBuilder.toString() == "THE World")
    }

  }

}
