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
