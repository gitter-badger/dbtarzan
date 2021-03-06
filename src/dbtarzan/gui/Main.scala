package dbtarzan.gui

import java.io.File
import scalafx.application.JFXApp
import scalafx.application.JFXApp.PrimaryStage
import scalafx.collections.ObservableBuffer 
import scalafx.scene.Scene
import scalafx.stage.Screen
import scalafx.scene.control.SplitPane
import scalafx.scene.image.Image
import scalafx.scene.layout.GridPane
import scalafx.geometry.Orientation
import scalafx.beans.property.{StringProperty, ObjectProperty}
import scalafx.Includes._
import scala.util.{Try, Success, Failure}
import dbtarzan.gui.util.JFXUtil
import dbtarzan.config.{ Config, ConfigReader }
import dbtarzan.db.ConnectionBuilder
import akka.actor.{ ActorSystem, Props, ActorRef }
import dbtarzan.gui.actor.GUIWorker
import dbtarzan.config.actor.ConfigWorker
import dbtarzan.messages.{ QueryTables, QueryDatabase, CopyToFile }


/**
  Main class, containing everything
*/
object Main extends JFXApp {
  val version = "0.96"
  val system = ActorSystem("Sys")
  val databaseTabs = new DatabaseTabs(system)
  val logList = new LogList()
  val config = new Config(ConfigReader.read("connections.config"))
  val guiActor = system.actorOf(Props(new GUIWorker(databaseTabs, logList)).withDispatcher("my-pinned-dispatcher"), "guiWorker")
  val configActor = system.actorOf(Props(new ConfigWorker(config, guiActor)).withDispatcher("my-pinned-dispatcher"), "configWorker")
  println("configWorker "+configActor)  
  val databaseList = new DatabaseList(config.connections)
  databaseList.onDatabaseSelected( { case databaseName => configActor ! QueryDatabase(databaseName) })
  databaseList.onForeignKeyToFile( { case databaseName => configActor ! CopyToFile(databaseName) })
  val screenBounds = Screen.primary.visualBounds
  stage = buildStage()

  private def buildDatabaseSplitPane() = new SplitPane {
      items.addAll(JFXUtil.withTitle(databaseList.control, "Databases"), databaseTabs.control)
      dividerPositions = 0.2
      SplitPane.setResizableWithParent(databaseList.control, false)
  }
  
  private def mainSplitPane() = new SplitPane {
      orientation() =  Orientation.VERTICAL
      items.addAll(buildDatabaseSplitPane(), logList.control)
      dividerPositions = 0.85
      SplitPane.setResizableWithParent(logList.control, false)
  }
  
  /* first we close the dbWorker actors. Then the config and gui actors. Then we stop the actor system and we check that there are no more actors. 
      Once this is done, we close JavaF (the GUI)
  */
  private def closeApp() : Unit = {
      println("application exit")
      databaseTabs.sendCloseToAllOpen()
      import akka.pattern.gracefulStop
      import scala.concurrent._
      import scala.concurrent.duration._
      import ExecutionContext.Implicits.global
      val stopAll = for {
        stopGui : Boolean <- gracefulStop(guiActor, 1 seconds)
        stopConfig : Boolean <- gracefulStop(configActor, 1 seconds)
      } yield stopGui && stopConfig
      stopAll.foreach(x => { 
        system.shutdown()
        println("shutdown")
        system.registerOnTermination(() => scalafx.application.Platform.exit())
      })
  }

  private def appIcon() = 
    new Image(getClass().getResourceAsStream("monkey-face-cartoon.png"))

  def buildStage() = new PrimaryStage {
    title = "DbTarzan "+version
    icons.add(appIcon())
    scene = new Scene(screenBounds.width / 2, screenBounds.height / 2 ) {
        root = mainSplitPane()
        onCloseRequest = handle { closeApp() }
      }
  }
}

