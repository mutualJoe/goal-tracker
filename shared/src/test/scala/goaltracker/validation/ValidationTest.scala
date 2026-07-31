package goaltracker.validation

import munit.FunSuite

class ValidationTest extends FunSuite:

  test("rejects empty habit title") {
    val result = Validation.habit("", "desc", "#AABBCC", 3)
    assert(!result.isValid)
  }

  test("accepts valid habit") {
    val result = Validation.habit("Morning run", "easy pace", "#3D5A40", 4)
    assert(result.isValid)
  }

  test("rejects weak password") {
    val result = Validation.auth("alice", "123")
    assert(!result.isValid)
  }
