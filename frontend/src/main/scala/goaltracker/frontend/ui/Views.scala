package goaltracker.frontend.ui

import com.raquo.laminar.api.L.*
import goaltracker.crdt.Merge
import goaltracker.frontend.state.AppState
import goaltracker.model.*

object Views:

  def app: HtmlElement =
    div(
      cls := "shell",
      headerTag(
        cls := "top",
        div(
          cls := "brand-block",
          p(cls := "brand", "Grove"),
          p(cls := "tagline", "Habits & goals that live on your device first")
        ),
        navTag(
          cls := "tabs",
          tabButton("habits", "Habits"),
          tabButton("goals", "Goals"),
          tabButton("sync", "Sync")
        )
      ),
      p(cls := "status", child.text <-- AppState.statusVar.signal),
      div(
        cls := "panel",
        child <-- AppState.panelVar.signal.map {
          case "goals" => goalsPanel
          case "sync"  => syncPanel
          case _       => habitsPanel
        }
      )
    )

  private def tabButton(id: String, label: String): HtmlElement =
    button(
      cls := "tab",
      cls("active") <-- AppState.panelVar.signal.map(_ == id),
      onClick.mapTo(id) --> AppState.panelVar,
      label
    )

  private def habitsPanel: HtmlElement =
    val titleVar = Var("")
    val descVar = Var("")
    val colorVar = Var("#3D5A40")
    val targetVar = Var("5")

    div(
      cls := "stack",
      sectionTag(
        cls := "composer",
        h2("New habit"),
        div(
          cls := "form-grid",
          labeledInput("Title", titleVar, "Morning stretch"),
          labeledInput("Description", descVar, "Optional note"),
          labeledInput("Color", colorVar, "#3D5A40"),
          labeledInput("Target / week", targetVar, "5")
        ),
        button(
          cls := "primary",
          "Add habit",
          onClick --> { _ =>
            AppState
              .addHabit(
                titleVar.now(),
                descVar.now(),
                colorVar.now(),
                targetVar.now().toIntOption.getOrElse(0)
              )
              .left
              .foreach(AppState.statusVar.set)
            titleVar.set("")
            descVar.set("")
          }
        )
      ),
      sectionTag(
        cls := "list-section",
        h2("Today"),
        p(cls := "muted", s"Week of ${AppState.weekDates.head} · check-ins are local until you sync"),
        children <-- AppState.documentVar.signal.map { doc =>
          val habits = Merge.activeHabits(doc)
          if habits.isEmpty then List(p(cls := "empty", "No habits yet — add one above."))
          else habits.map(habitRow(doc, _))
        }
      )
    )

  private def habitRow(doc: SyncDocument, habit: Habit): HtmlElement =
    val doneToday = Merge.checkInForDate(doc, habit.id, AppState.todayDate).exists(_.done)
    val weekDone = Merge.weeklyCount(doc, habit.id, AppState.weekDates)
    val pct = (weekDone.toDouble / habit.targetPerWeek.toDouble * 100).min(100)

    articleTag(
      cls := "habit-row",
      styleAttr := s"--accent: ${habit.color}",
      button(
        cls := "check",
        cls("checked") := doneToday,
        aria.label := s"Toggle ${habit.title} for today",
        onClick --> { _ => AppState.toggleCheckIn(habit.id) },
        if doneToday then "✓" else ""
      ),
      div(
        cls := "habit-meta",
        h3(habit.title),
        p(cls := "muted", if habit.description.isEmpty then s"${habit.targetPerWeek}/week" else habit.description),
        div(
          cls := "meter",
          div(cls := "meter-fill", styleAttr := s"width: $pct%")
        ),
        p(cls := "meter-label", s"$weekDone / ${habit.targetPerWeek} this week")
      ),
      button(
        cls := "ghost danger",
        "Remove",
        onClick --> { _ => AppState.deleteHabit(habit.id) }
      )
    )

  private def goalsPanel: HtmlElement =
    val titleVar = Var("")
    val descVar = Var("")
    val targetVar = Var("10")
    val unitVar = Var("books")
    val deadlineVar = Var("")

    div(
      cls := "stack",
      sectionTag(
        cls := "composer",
        h2("New goal"),
        div(
          cls := "form-grid",
          labeledInput("Title", titleVar, "Read more"),
          labeledInput("Description", descVar, "Optional"),
          labeledInput("Target", targetVar, "12"),
          labeledInput("Unit", unitVar, "books"),
          labeledInput("Deadline", deadlineVar, "YYYY-MM-DD (optional)")
        ),
        button(
          cls := "primary",
          "Add goal",
          onClick --> { _ =>
            val dl = Option(deadlineVar.now().trim).filter(_.nonEmpty)
            AppState
              .addGoal(titleVar.now(), descVar.now(), targetVar.now().toIntOption.getOrElse(0), unitVar.now(), dl)
              .left
              .foreach(AppState.statusVar.set)
            titleVar.set("")
            descVar.set("")
          }
        )
      ),
      sectionTag(
        cls := "list-section",
        h2("In progress"),
        children <-- AppState.documentVar.signal.map { doc =>
          val goals = Merge.activeGoals(doc)
          if goals.isEmpty then List(p(cls := "empty", "No goals yet — set a target above."))
          else goals.map(goalRow)
        }
      )
    )

  private def goalRow(goal: Goal): HtmlElement =
    val pct = if goal.targetValue == 0 then 0.0
    else goal.currentValue.toDouble / goal.targetValue.toDouble * 100

    articleTag(
      cls := "goal-row",
      div(
        cls := "goal-meta",
        h3(goal.title),
        p(
          cls := "muted",
          s"${goal.currentValue} / ${goal.targetValue} ${goal.unit}" +
            goal.deadline.map(d => s" · due $d").getOrElse("")
        ),
        div(cls := "meter", div(cls := "meter-fill", styleAttr := s"width: ${pct.min(100)}%"))
      ),
      div(
        cls := "goal-actions",
        button(cls := "ghost", "−1", onClick --> { _ => AppState.bumpGoal(goal.id, -1) }),
        button(cls := "primary small", "+1", onClick --> { _ => AppState.bumpGoal(goal.id, 1) }),
        button(cls := "ghost danger", "Remove", onClick --> { _ => AppState.deleteGoal(goal.id) })
      )
    )

  private def syncPanel: HtmlElement =
    val userVar = Var("")
    val passVar = Var("")
    val urlVar = Var(AppState.serverUrlVar.now())

    div(
      cls := "stack",
      sectionTag(
        cls := "composer",
        h2("Optional sync"),
        p(
          cls := "muted",
          "Grove works fully offline. Sign in only when you want multi-device merge via the http4s backend."
        ),
        div(
          cls := "form-grid",
          labeledInput("Server URL", urlVar, "http://localhost:8080"),
          labeledInput("Username", userVar, "you"),
          div(
            cls := "field",
            label("Password"),
            input(
              typ := "password",
              placeholder := "••••••••",
              controlled(value <-- passVar.signal, onInput.mapToValue --> passVar)
            )
          )
        ),
        div(
          cls := "row",
          button(
            cls := "ghost",
            "Save URL",
            onClick --> { _ => AppState.saveServerUrl(urlVar.now()) }
          ),
          button(
            cls := "ghost",
            "Register",
            onClick --> { _ => AppState.register(userVar.now(), passVar.now()) }
          ),
          button(
            cls := "primary",
            "Sign in",
            onClick --> { _ => AppState.login(userVar.now(), passVar.now()) }
          )
        )
      ),
      sectionTag(
        cls := "composer",
        h2("Session"),
        child <-- AppState.authVar.signal.map {
          case None =>
            p(cls := "muted", "Not signed in. Local storage is the source of truth.")
          case Some(s) =>
            div(
              cls := "stack-sm",
              p(s"Signed in as ${s.username}"),
              div(
                cls := "row",
                button(cls := "primary", "Sync now", onClick --> { _ => AppState.syncNow() }),
                button(cls := "ghost", "Sign out", onClick --> { _ => AppState.logout() })
              )
            )
        },
        p(
          cls := "muted fine",
          "Merge uses LWW registers + tombstones from the shared module — same logic on client and server."
        )
      )
    )

  private def labeledInput(name: String, state: Var[String], hint: String): HtmlElement =
    div(
      cls := "field",
      label(name),
      input(
        typ := "text",
        placeholder := hint,
        controlled(value <-- state.signal, onInput.mapToValue --> state)
      )
    )
