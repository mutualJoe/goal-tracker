package goaltracker.model

/** Versioned entity used by LWW (last-writer-wins) merge. */
trait Versioned:
  def updatedAt: Long
  def deviceId: DeviceId
  def deleted: Boolean

final case class Habit(
    id: HabitId,
    title: String,
    description: String,
    color: String,
    targetPerWeek: Int,
    createdAt: Long,
    updatedAt: Long,
    deleted: Boolean,
    deviceId: DeviceId
) extends Versioned

final case class HabitCheckIn(
    id: CheckInId,
    habitId: HabitId,
    date: String, // YYYY-MM-DD (local calendar day)
    done: Boolean,
    updatedAt: Long,
    deleted: Boolean,
    deviceId: DeviceId
) extends Versioned

final case class Goal(
    id: GoalId,
    title: String,
    description: String,
    targetValue: Int,
    currentValue: Int,
    unit: String,
    deadline: Option[String],
    createdAt: Long,
    updatedAt: Long,
    deleted: Boolean,
    deviceId: DeviceId
) extends Versioned

final case class SyncDocument(
    habits: List[Habit],
    checkIns: List[HabitCheckIn],
    goals: List[Goal]
)

object SyncDocument:
  val empty: SyncDocument = SyncDocument(Nil, Nil, Nil)

final case class AuthRequest(username: String, password: String)
final case class AuthResponse(token: String, userId: UserId, username: String)

final case class SyncRequest(document: SyncDocument)
final case class SyncResponse(document: SyncDocument, serverTime: Long)
