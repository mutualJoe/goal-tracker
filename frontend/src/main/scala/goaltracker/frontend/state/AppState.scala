package goaltracker.frontend.state

import com.raquo.laminar.api.L.*
import goaltracker.crdt.Merge
import goaltracker.frontend.storage.LocalStore
import goaltracker.frontend.sync.SyncClient
import goaltracker.model.*
import goaltracker.validation.Validation
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

object AppState:
  private val device = LocalStore.deviceId()

  val documentVar: Var[SyncDocument] = Var(LocalStore.loadDocument())
  val statusVar: Var[String] = Var("Local-only · changes stay on this device")
  val authVar: Var[Option[LocalStore.AuthSession]] = Var(LocalStore.loadAuth())
  val serverUrlVar: Var[String] = Var(LocalStore.serverUrl())
  val panelVar: Var[String] = Var("habits") // habits | goals | sync

  documentVar.signal.foreach { doc =>
    LocalStore.saveDocument(doc)
  }(using unsafeWindowOwner)

  private def now(): Long = System.currentTimeMillis()

  private def today(): String =
    val d = new scala.scalajs.js.Date()
    f"${d.getFullYear().toInt}%04d-${d.getMonth().toInt + 1}%02d-${d.getDate().toInt}%02d"

  def todayDate: String = today()

  def weekDates: List[String] =
    val d = new scala.scalajs.js.Date()
    val day = d.getDay().toInt // 0 = Sunday
    val mondayOffset = if day == 0 then -6 else 1 - day
    (0 until 7).toList.map { i =>
      val copy = new scala.scalajs.js.Date(d.getTime())
      copy.setDate(d.getDate() + mondayOffset + i)
      f"${copy.getFullYear().toInt}%04d-${copy.getMonth().toInt + 1}%02d-${copy.getDate().toInt}%02d"
    }

  def addHabit(title: String, description: String, color: String, targetPerWeek: Int): Either[String, Unit] =
    Validation.habit(title, description, color, targetPerWeek).toEither.left
      .map(_.map(_.message).mkString("; "))
      .map { case (t, desc, c, target) =>
        val ts = now()
        val habit = Habit(
          id = HabitId(Ids.newId()),
          title = t,
          description = desc,
          color = c,
          targetPerWeek = target,
          createdAt = ts,
          updatedAt = ts,
          deleted = false,
          deviceId = device
        )
        documentVar.update(doc => doc.copy(habits = habit :: doc.habits))
        statusVar.set(s"Added habit “$t”")
      }

  def deleteHabit(id: HabitId): Unit =
    documentVar.update { doc =>
      val habits = doc.habits.map { h =>
        if h.id == id then h.copy(deleted = true, updatedAt = now(), deviceId = device) else h
      }
      doc.copy(habits = habits)
    }
    statusVar.set("Habit removed (tombstone synced later)")

  def toggleCheckIn(habitId: HabitId): Unit =
    val date = today()
    documentVar.update { doc =>
      Merge.checkInForDate(doc, habitId, date) match
        case Some(existing) =>
          val updated = existing.copy(
            done = !existing.done,
            updatedAt = now(),
            deviceId = device,
            deleted = false
          )
          doc.copy(checkIns = doc.checkIns.map(c => if c.id == existing.id then updated else c))
        case None =>
          val created = HabitCheckIn(
            id = CheckInId(Ids.newId()),
            habitId = habitId,
            date = date,
            done = true,
            updatedAt = now(),
            deleted = false,
            deviceId = device
          )
          doc.copy(checkIns = created :: doc.checkIns)
    }

  def addGoal(
      title: String,
      description: String,
      targetValue: Int,
      unit: String,
      deadline: Option[String]
  ): Either[String, Unit] =
    Validation.goal(title, description, targetValue, 0, unit, deadline).toEither.left
      .map(_.map(_.message).mkString("; "))
      .map { case (t, desc, target, current, u, dl) =>
        val ts = now()
        val goal = Goal(
          id = GoalId(Ids.newId()),
          title = t,
          description = desc,
          targetValue = target,
          currentValue = current,
          unit = u,
          deadline = dl,
          createdAt = ts,
          updatedAt = ts,
          deleted = false,
          deviceId = device
        )
        documentVar.update(doc => doc.copy(goals = goal :: doc.goals))
        statusVar.set(s"Added goal “$t”")
      }

  def bumpGoal(id: GoalId, delta: Int): Unit =
    documentVar.update { doc =>
      val goals = doc.goals.map { g =>
        if g.id == id && !g.deleted then
          val next = (g.currentValue + delta).max(0).min(g.targetValue)
          g.copy(currentValue = next, updatedAt = now(), deviceId = device)
        else g
      }
      doc.copy(goals = goals)
    }

  def deleteGoal(id: GoalId): Unit =
    documentVar.update { doc =>
      val goals = doc.goals.map { g =>
        if g.id == id then g.copy(deleted = true, updatedAt = now(), deviceId = device) else g
      }
      doc.copy(goals = goals)
    }

  def saveServerUrl(url: String): Unit =
    LocalStore.saveServerUrl(url)
    serverUrlVar.set(url.trim)
    statusVar.set("Sync server URL saved")

  def register(username: String, password: String): Unit =
    statusVar.set("Registering…")
    SyncClient.register(serverUrlVar.now(), username, password).foreach {
      case Left(err) => statusVar.set(err.message)
      case Right(res) =>
        val session = LocalStore.AuthSession(res.token, res.userId.value, res.username)
        LocalStore.saveAuth(session)
        authVar.set(Some(session))
        statusVar.set(s"Registered as ${res.username}")
    }

  def login(username: String, password: String): Unit =
    statusVar.set("Signing in…")
    SyncClient.login(serverUrlVar.now(), username, password).foreach {
      case Left(err) => statusVar.set(err.message)
      case Right(res) =>
        val session = LocalStore.AuthSession(res.token, res.userId.value, res.username)
        LocalStore.saveAuth(session)
        authVar.set(Some(session))
        statusVar.set(s"Signed in as ${res.username}")
    }

  def logout(): Unit =
    LocalStore.clearAuth()
    authVar.set(None)
    statusVar.set("Signed out · still local-first")

  def syncNow(): Unit =
    authVar.now() match
      case None => statusVar.set("Sign in on the Sync panel to enable multi-device sync")
      case Some(session) =>
        statusVar.set("Syncing…")
        SyncClient.sync(serverUrlVar.now(), session.token, documentVar.now()).foreach {
          case Left(err) => statusVar.set(err.message)
          case Right(res) =>
            val merged = Merge.mergeDocuments(documentVar.now(), res.document)
            documentVar.set(merged)
            statusVar.set(s"Synced · server time ${res.serverTime}")
        }
