package dbtarzan.config.actor

import akka.actor.Actor
import akka.actor.ActorRef
import akka.routing.Broadcast
import dbtarzan.messages._
import dbtarzan.config.Config
import dbtarzan.db.ConnectionBuilder
import scala.collection.mutable.HashMap

class ConfigWorker(config : Config, guiActor : ActorRef) extends Actor {
	 private val mapDBWorker = HashMap.empty[String, ActorRef]

	 private def getDBWorker(databaseName : String) : ActorRef = {
	    	val data = config.connect(databaseName)
    		val dbActor = ConnectionBuilder.buildDBWorker(data, guiActor, context)
    		mapDBWorker += databaseName -> dbActor
    		dbActor
	 } 


	 private def startCopyWorker(databaseName : String) : Unit = {
	    	val data = config.connect(databaseName)
    		val copyActor = ConnectionBuilder.buildCopyWorker(data, guiActor, context)
    		copyActor ! CopyToFile
	 } 


	 private def queryDatabase(databaseName : String) : Unit = {
	    	println("Querying the database "+databaseName)
	    	try {
	    		if(!mapDBWorker.isDefinedAt(databaseName))
	    			guiActor ! ResponseDatabase(databaseName, getDBWorker(databaseName))
	    		else
	    			guiActor ! ErrorDatabaseAlreadyOpen(databaseName)
			} catch {
				case e : Exception => guiActor ! Error(e)	    	
			}	 	
	 }

	 private def queryClose(databaseName : String) : Unit = {
	    println("Closing the database "+databaseName) 
	 	mapDBWorker.remove(databaseName).foreach(
	 		dbActor => dbActor ! Broadcast(QueryClose(databaseName)) // routed to all dbWorkers of the router
	 		)
	 }

	 def receive = {
	    case qry : QueryDatabase => queryDatabase(qry.databaseName)
	    case qry : QueryClose => queryClose(qry.databaseName)
	    case cpy : CopyToFile => startCopyWorker(cpy.databaseName)
	}
}