package goaltracker.crdt

import goaltracker.model.*

/** CRDT-style merge helpers for offline-first sync.
  *
  * Strategy:
  *  - Entities are LWW-Registers keyed by id (updatedAt, then deviceId).
  *  - Soft deletes use tombstones (`deleted = true`) so removals replicate.
  *  - Collections are LWW-Maps: union keys, merge each value independently.
  */
object Merge:

  /** Compare version metadata: higher timestamp wins; ties break on deviceId. */
  def wins[A <: Versioned](a: A, b: A): A =
    if b.updatedAt > a.updatedAt then b
    else if b.updatedAt < a.updatedAt then a
    else if b.deviceId.value > a.deviceId.value then b
    else a

  def mergeById[A <: Versioned, K](local: List[A], remote: List[A], key: A => K): List[A] =
    val map = scala.collection.mutable.LinkedHashMap.empty[K, A]
    local.foreach(item => map.update(key(item), item))
    remote.foreach { item =>
      val k = key(item)
      map.update(k, map.get(k).map(wins(_, item)).getOrElse(item))
    }
    map.values.toList

  def mergeDocuments(local: SyncDocument, remote: SyncDocument): SyncDocument =
    SyncDocument(
      habits = mergeById(local.habits, remote.habits, _.id),
      checkIns = mergeById(local.checkIns, remote.checkIns, _.id),
      goals = mergeById(local.goals, remote.goals, _.id)
    )

  def activeHabits(doc: SyncDocument): List[Habit] =
    doc.habits.filterNot(_.deleted).sortBy(_.createdAt)

  def activeGoals(doc: SyncDocument): List[Goal] =
    doc.goals.filterNot(_.deleted).sortBy(_.createdAt)

  def activeCheckIns(doc: SyncDocument, habitId: HabitId): List[HabitCheckIn] =
    doc.checkIns.filter(c => c.habitId == habitId && !c.deleted)

  def checkInForDate(doc: SyncDocument, habitId: HabitId, date: String): Option[HabitCheckIn] =
    activeCheckIns(doc, habitId).find(_.date == date)

  def weeklyCount(doc: SyncDocument, habitId: HabitId, weekDates: List[String]): Int =
    weekDates.count { d =>
      checkInForDate(doc, habitId, d).exists(_.done)
    }

  /** Done check-ins today, and how many active habits exist. */
  def todayProgress(doc: SyncDocument, date: String): (Int, Int) =
    val habits = activeHabits(doc)
    val done = habits.count(h => checkInForDate(doc, h.id, date).exists(_.done))
    (done, habits.size)

  /** Consecutive days with at least one completed check-in.
    *
    * Today still counts as open: if nothing is checked yet, the streak
    * continues from yesterday instead of dropping to zero.
    */
  def currentStreak(doc: SyncDocument, today: String): Int =
    val habits = activeHabits(doc)
    if habits.isEmpty then 0
    else
      def dayDone(date: String): Boolean =
        habits.exists(h => checkInForDate(doc, h.id, date).exists(_.done))

      val anchor = if dayDone(today) then today else previousDay(today)
      Iterator.iterate(anchor)(previousDay).take(366).takeWhile(dayDone).length

  /** Previous calendar day for a `YYYY-MM-DD` string. No `java.time` — Scala.js does not link it. */
  def previousDay(date: String): String =
    val parts = date.split("-")
    var year = parts(0).toInt
    var month = parts(1).toInt
    var day = parts(2).toInt - 1
    if day < 1 then
      month -= 1
      if month < 1 then
        month = 12
        year -= 1
      day = daysInMonth(year, month)
    f"$year%04d-$month%02d-$day%02d"

  private def daysInMonth(year: Int, month: Int): Int =
    val leap = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
    month match
      case 2               => if leap then 29 else 28
      case 4 | 6 | 9 | 11  => 30
      case _               => 31
