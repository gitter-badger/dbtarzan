package dbtarzan.db.actor

import java.sql.{Connection, ResultSet}
import scala.collection.mutable.ListBuffer
import dbtarzan.db.{ ForeignKey, ForeignKeys, FieldsOnTable }

class ForeignKeyLoader(connection : java.sql.Connection) {
	case class ForeignKeyKey(name: String, fromTable : String, toTable : String)
	case class ForeignKeyPart(key : ForeignKeyKey, fromField : String, toField : String)

	private def rsToForeignPart(rs : ResultSet) : ForeignKeyPart = 
		ForeignKeyPart(
				ForeignKeyKey(
					rs.getString("FK_NAME"), 
					rs.getString("FKTABLE_NAME"), 
					rs.getString("PKTABLE_NAME")
				), 
				rs.getString("FKCOLUMN_NAME"), 				
				rs.getString("PKCOLUMN_NAME")
			)

	private def foreignPartsToForeignKeys(list : List[ForeignKeyPart]) : List[ForeignKey] = {
		val mapByKey = list.groupBy(part => part.key)
		mapByKey .toList.map({case (key, listOfKey) => 
			foreignKeyPartToForeignKey(key,  
				listOfKey.map(_.fromField).toList, 
				listOfKey.map(_.toField).toList
			)
		})
	}

	private def foreignKeyPartToForeignKey(key : ForeignKeyKey, from : List[String], to : List[String]) = 
		ForeignKey(key.name, 
			FieldsOnTable(key.fromTable, from),
			FieldsOnTable(key.toTable, to)
			)


	private def rsToForeignKeys(rs : ResultSet) : List[ForeignKey] = {
		val list = new ListBuffer[ForeignKeyPart]()
		while(rs.next) 
			list += rsToForeignPart(rs)			
		foreignPartsToForeignKeys(list.toList)
	}

	private def turnForeignKey(key : ForeignKey) =
		ForeignKey(key.name, key.to, key.from)

	/**
		All the foreign keys from the table and TO the table (used in reverse order)
	*/
	def foreignKeys(tableName : String, schema: Option[String]) : ForeignKeys = {
		var meta = connection.getMetaData()
		var rsImported = meta.getImportedKeys(null, schema.orNull, tableName)
		var rsExported = meta.getExportedKeys(null, schema.orNull, tableName)
		val keysImported = rsToForeignKeys(rsImported) 
		val keysExported = rsToForeignKeys(rsExported).map(turnForeignKey(_)) 
		println("keysImported:"+keysImported+"\nkeysExported:"+keysExported)
		val keys = keysImported ++ keysExported 

		ForeignKeys(keys)
	}
}