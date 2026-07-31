package goaltracker.model

final case class HabitId(value: String)
final case class GoalId(value: String)
final case class CheckInId(value: String)
final case class DeviceId(value: String)
final case class UserId(value: String)

object Ids:
  def newId(): String =
    val millis = System.currentTimeMillis().toHexString
    val rand = scala.util.Random.alphanumeric.take(8).mkString
    s"$millis-$rand"
