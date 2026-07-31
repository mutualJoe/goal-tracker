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
