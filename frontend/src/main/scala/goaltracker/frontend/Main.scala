package goaltracker.frontend

import com.raquo.laminar.api.L.*
import goaltracker.frontend.ui.Views
import org.scalajs.dom

object Main:
  def main(args: Array[String]): Unit =
    val container = Option(dom.document.getElementById("app")).getOrElse {
      val el = dom.document.createElement("div")
      el.id = "app"
      dom.document.body.appendChild(el)
      el
    }
    render(container, Views.app)
