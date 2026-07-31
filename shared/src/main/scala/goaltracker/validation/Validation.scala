package goaltracker.validation

import goaltracker.model.*

enum FieldError(val message: String):
  case Empty(field: String) extends FieldError(s"$field must not be empty")
  case TooLong(field: String, max: Int) extends FieldError(s"$field must be at most $max characters")
  case OutOfRange(field: String, min: Int, max: Int)
      extends FieldError(s"$field must be between $min and $max")
  case InvalidDate(field: String) extends FieldError(s"$field must be YYYY-MM-DD")
  case InvalidColor(field: String) extends FieldError(s"$field must be a #RRGGBB color")

final case class ValidationResult[A](value: Option[A], errors: List[FieldError]):
  def isValid: Boolean = errors.isEmpty && value.isDefined
  def toEither: Either[List[FieldError], A] =
    value.toRight(errors).flatMap(v => if errors.isEmpty then Right(v) else Left(errors))

object Validation:
  private val datePattern = raw"\d{4}-\d{2}-\d{2}".r
  private val colorPattern = raw"#[0-9A-Fa-f]{6}".r

  def nonEmpty(field: String, value: String, max: Int = 120): List[FieldError] =
    val trimmed = value.trim
    if trimmed.isEmpty then List(FieldError.Empty(field))
    else if trimmed.length > max then List(FieldError.TooLong(field, max))
    else Nil

  def inRange(field: String, value: Int, min: Int, max: Int): List[FieldError] =
    if value < min || value > max then List(FieldError.OutOfRange(field, min, max)) else Nil

  def validDate(field: String, value: String): List[FieldError] =
    if datePattern.matches(value) then Nil else List(FieldError.InvalidDate(field))

  def validColor(field: String, value: String): List[FieldError] =
    if colorPattern.matches(value) then Nil else List(FieldError.InvalidColor(field))

  def habit(
      title: String,
      description: String,
      color: String,
      targetPerWeek: Int
  ): ValidationResult[(String, String, String, Int)] =
    val errors =
      nonEmpty("title", title, 80) ++
        (if description.trim.length > 280 then List(FieldError.TooLong("description", 280)) else Nil) ++
        validColor("color", color) ++
        inRange("targetPerWeek", targetPerWeek, 1, 21)
    if errors.isEmpty then
      ValidationResult(Some((title.trim, description.trim, color, targetPerWeek)), Nil)
    else ValidationResult(None, errors)

  def goal(
      title: String,
      description: String,
      targetValue: Int,
      currentValue: Int,
      unit: String,
      deadline: Option[String]
  ): ValidationResult[(String, String, Int, Int, String, Option[String])] =
    val deadlineErrors = deadline.toList.flatMap(d => validDate("deadline", d))
    val errors =
      nonEmpty("title", title, 80) ++
        (if description.trim.length > 280 then List(FieldError.TooLong("description", 280)) else Nil) ++
        inRange("targetValue", targetValue, 1, 1_000_000) ++
        inRange("currentValue", currentValue, 0, 1_000_000) ++
        nonEmpty("unit", unit, 24) ++
        deadlineErrors ++
        (if currentValue > targetValue then List(FieldError.OutOfRange("currentValue", 0, targetValue))
         else Nil)
    if errors.isEmpty then
      ValidationResult(
        Some((title.trim, description.trim, targetValue, currentValue, unit.trim, deadline)),
        Nil
      )
    else ValidationResult(None, errors)

  def auth(username: String, password: String): ValidationResult[(String, String)] =
    val errors =
      nonEmpty("username", username, 40) ++
        (if password.length < 6 then List(FieldError.OutOfRange("password", 6, 128))
         else if password.length > 128 then List(FieldError.TooLong("password", 128))
         else Nil)
    if errors.isEmpty then ValidationResult(Some((username.trim, password)), Nil)
    else ValidationResult(None, errors)
