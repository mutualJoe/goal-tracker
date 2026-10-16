package goaltracker.crdt

import goaltracker.model.*
import munit.FunSuite

class MergeTest extends FunSuite:

  private val deviceA = DeviceId("device-a")
  private val deviceB = DeviceId("device-b")

  private def habit(id: String, title: String, at: Long, device: DeviceId, deleted: Boolean = false) =
    Habit(
      id = HabitId(id),
      title = title,
      description = "",
      color = "#3D5A40",
      targetPerWeek = 5,
      createdAt = at,
      updatedAt = at,
      deleted = deleted,
      deviceId = device
    )

  test("later timestamp wins") {
    val local = habit("1", "Local", 100, deviceA)
    val remote = habit("1", "Remote", 200, deviceB)
    assertEquals(Merge.wins(local, remote).title, "Remote")
  }

  test("equal timestamp breaks ties by deviceId") {
    val local = habit("1", "Local", 100, deviceA)
    val remote = habit("1", "Remote", 100, deviceB)
    assertEquals(Merge.wins(local, remote).title, "Remote")
  }

  test("tombstones replicate through merge") {
    val local = SyncDocument(List(habit("1", "Run", 100, deviceA)), Nil, Nil)
    val remote = SyncDocument(List(habit("1", "Run", 150, deviceB, deleted = true)), Nil, Nil)
    val merged = Merge.mergeDocuments(local, remote)
    assert(merged.habits.head.deleted)
    assertEquals(Merge.activeHabits(merged), Nil)
  }

  test("union of distinct ids is preserved") {
    val local = SyncDocument(List(habit("1", "Run", 100, deviceA)), Nil, Nil)
    val remote = SyncDocument(List(habit("2", "Read", 120, deviceB)), Nil, Nil)
    val merged = Merge.mergeDocuments(local, remote)
    assertEquals(merged.habits.map(_.id.value).sorted, List("1", "2"))
  }

  private def checkIn(habitId: String, date: String, done: Boolean = true) =
    HabitCheckIn(
      id = CheckInId(s"$habitId-$date"),
      habitId = HabitId(habitId),
      date = date,
      done = done,
      updatedAt = 1,
      deleted = false,
      deviceId = deviceA
    )

  test("today progress counts completed habits") {
    val doc = SyncDocument(
      List(habit("1", "Run", 100, deviceA), habit("2", "Read", 110, deviceA)),
      List(checkIn("1", "2026-10-16"), checkIn("2", "2026-10-16", done = false)),
      Nil
    )
    assertEquals(Merge.todayProgress(doc, "2026-10-16"), (1, 2))
  }

  test("streak keeps yesterday when today is still open") {
    val doc = SyncDocument(
      List(habit("1", "Run", 100, deviceA)),
      List(checkIn("1", "2026-10-14"), checkIn("1", "2026-10-15")),
      Nil
    )
    assertEquals(Merge.currentStreak(doc, "2026-10-16"), 2)
  }

  test("previous day crosses month and leap-day boundaries") {
    assertEquals(Merge.previousDay("2026-10-01"), "2026-09-30")
    assertEquals(Merge.previousDay("2024-03-01"), "2024-02-29")
    assertEquals(Merge.previousDay("2026-03-01"), "2026-02-28")
  }

  test("streak includes today once a habit is checked") {
    val doc = SyncDocument(
      List(habit("1", "Run", 100, deviceA)),
      List(checkIn("1", "2026-10-15"), checkIn("1", "2026-10-16")),
      Nil
    )
    assertEquals(Merge.currentStreak(doc, "2026-10-16"), 2)
  }
