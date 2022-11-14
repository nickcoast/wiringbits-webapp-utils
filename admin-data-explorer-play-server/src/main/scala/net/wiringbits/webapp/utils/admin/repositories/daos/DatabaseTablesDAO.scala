package net.wiringbits.webapp.utils.admin.repositories.daos

import net.wiringbits.webapp.utils.admin.config.TableSettings
import net.wiringbits.webapp.utils.admin.repositories.models.{Cell, DatabaseTable, ForeignKey, TableColumn, TableRow}
import net.wiringbits.webapp.utils.admin.utils.QueryBuilder
import net.wiringbits.webapp.utils.admin.utils.models.QueryParameters

import anorm.{SqlParser, SqlStringInterpolation}

import java.sql.{Connection, PreparedStatement}
import java.util.UUID
import scala.collection.mutable.ListBuffer
import scala.util.Try

object DatabaseTablesDAO {

  def all(schema: String = "public")(implicit conn: Connection): List[DatabaseTable] = {
    SQL"""
    SELECT table_name
    FROM information_schema.tables
    WHERE table_schema = $schema
      AND table_type = 'BASE TABLE'
    ORDER BY table_name
    """.as(tableParser.*)
  }

  def getTableColumns(
      tableName: String
  )(implicit conn: Connection): List[TableColumn] = {
    val sql = s"SELECT * FROM $tableName LIMIT 0"
    val preparedStatement = conn.prepareStatement(sql)

    try {
      val resultSet = preparedStatement.executeQuery()
      val metadata = resultSet.getMetaData
      val numberOfColumns = metadata.getColumnCount
      try {
        val fields = for {
          columnNumber <- 1 to numberOfColumns
          columnName = metadata.getColumnName(columnNumber)
          columnType = metadata.getColumnTypeName(columnNumber)
        } yield TableColumn(columnName, columnType)
        fields.toList
      } finally {
        resultSet.close()
      }
    } finally {
      preparedStatement.close()
    }
  }

  def getForeignKeys(
      tableName: String
  )(implicit conn: Connection): List[ForeignKey] = {
    SQL"""
    SELECT kcu.table_name AS foreign_table, 
      rel_tco.table_name AS primary_table, 
      kcu.column_name AS fk_column
    FROM information_schema.table_constraints tco
    JOIN information_schema.key_column_usage kcu
      ON tco.constraint_schema = kcu.constraint_schema
      AND tco.constraint_name = kcu.constraint_name
    JOIN information_schema.referential_constraints rco
      ON tco.constraint_schema = rco.constraint_schema
      AND tco.constraint_name = rco.constraint_name
    JOIN information_schema.table_constraints rel_tco
      ON rco.unique_constraint_schema = rel_tco.constraint_schema
      AND rco.unique_constraint_name = rel_tco.constraint_name
    WHERE tco.constraint_type = 'FOREIGN KEY'
      AND kcu.table_name = $tableName
    GROUP BY kcu.table_schema, kcu.table_name, kcu.column_name, rel_tco.table_name, rel_tco.table_schema
    ORDER BY kcu.table_schema, kcu.table_name
    """.as(foreignKeyParser.*)
  }

  def getTableData(
      tableName: String,
      fields: List[TableColumn],
      queryParameters: QueryParameters,
      settings: TableSettings
  )(implicit conn: Connection): List[TableRow] = {
    val limit = queryParameters.pagination.end - queryParameters.pagination.start
    val offset = queryParameters.pagination.start
    // react-admin gives us a "id" field instead of the primary key of the actual column so we need to replace it
    val sortBy = if (queryParameters.sort.field == "id") settings.primaryKeyField else queryParameters.sort.field

    val preparedStatement = queryParameters.filter.field map { field =>
      val primaryKey = if (field == "id") settings.primaryKeyField else field
      val sql =
        s"""
      SELECT * FROM $tableName
      WHERE $primaryKey = ?
      ORDER BY $sortBy ${queryParameters.sort.ordering}
      LIMIT ? OFFSET ?
      """
      val preparedStatement = conn.prepareStatement(sql)
      preparedStatement.setString(1, queryParameters.filter.value)
      preparedStatement.setInt(2, limit)
      preparedStatement.setInt(3, offset)
      preparedStatement
    } getOrElse {
      val sql =
        s"""
      SELECT * FROM $tableName
      ORDER BY $sortBy ${queryParameters.sort.ordering}
      LIMIT ? OFFSET ?
      """
      val preparedStatement = conn.prepareStatement(sql)
      preparedStatement.setInt(1, limit)
      preparedStatement.setInt(2, offset)
      preparedStatement
    }

    val resultSet = preparedStatement.executeQuery()
    val tableData = new ListBuffer[TableRow]()
    try {
      while (resultSet.next) {
        val rowData = for {
          field <- fields
          fieldName = field.name
          data = resultSet.getString(fieldName)
        } yield Cell(Option(data).getOrElse(""))
        tableData += TableRow(rowData)
      }
      tableData.toList
    } finally {
      resultSet.close()
      preparedStatement.close()
    }
  }

  def getMandatoryFields(tableName: String, primaryKeyField: String)(implicit conn: Connection): List[TableColumn] = {
    SQL"""
      SELECT column_name, data_type
      FROM information_schema.columns
      WHERE table_schema = 'public'
        AND is_nullable = 'NO'
        AND column_default IS NULL
        AND table_name = $tableName
        AND column_name != $primaryKeyField
      ORDER BY column_name
      """.as(tableColumnParser.*)
  }

  /*def pk[T](v: T)(implicit uuu :UUIDOrIntOrLongOrString[T] ): String = v match {
    case u: UUID => UUID.fromString(u.toString).toString() // I know this is funny
    case i: Int => i.toString
    case l: Long => l.toString
    case s: String => s
  }*/
  /*def pk[T](v: T)(implicit uuu: UUIDOrIntOrLongOrString[T]): String = v match {
    case u: UUID => UUID.fromString(u.toString).toString() // I know this is funny
    case i: Int => i.toString
    case l: Long => l.toString
    case s: String => s
  }*/
  def find(tableName: String, primaryKeyField: String, primaryKeyValue: String, primaryKeyType: String)(implicit
      conn: Connection
  ): Option[TableRow] = {
    val sql = s"""
    SELECT *
      FROM $tableName
    WHERE $primaryKeyField = ?
    """
    val preparedStatement = conn.prepareStatement(sql)
    /*//val primaryKeyString = pk(primaryKeyValue)
    val primaryKeyString = primaryKeyType match { // regular string unless UUID
      case "UUID" => preparedStatement.setObject(1, UUID.fromString(primaryKeyValue))
      case "Int" => preparedStatement.setInt(1,primaryKeyValue.toInt)
      case "Long" => preparedStatement.setLong(1,primaryKeyValue.toLong)
      case "String" => preparedStatement.setString(1,primaryKeyValue)
    }*/



    // TODO: UUID from String can fail if the ID field isn't an UUID
    //preparedStatement.setObject(1, primaryKeyString) // ok to pass ints as strings? or needs to be correct data type for prepared statement?
    setPreparedStatementKey(preparedStatement, primaryKeyValue, primaryKeyType)
    val resultSet = preparedStatement.executeQuery()
    Try {
      resultSet.next()
      val numberOfColumns = resultSet.getMetaData.getColumnCount
      val row = for {
        columnNumber <- 1 to numberOfColumns
        cellData = resultSet.getString(columnNumber)
      } yield Cell(Option(cellData).getOrElse(""))
      TableRow(row.toList)
    }.toOption
  }

  def setPreparedStatementKey(preparedStatement: PreparedStatement, primaryKeyValue: String, primaryKeyType: String): Unit = {
    primaryKeyType match { // regular string unless UUID
      case "UUID" => preparedStatement.setObject(1, UUID.fromString(primaryKeyValue))
      case _ => preparedStatement.setInt(1, primaryKeyValue.toInt)
    }
  }
  def create(tableName: String, body: Map[String, String], primaryKeyField: String, primaryKeyType: String)(implicit
      conn: Connection
  ): Unit = {
    val sql = QueryBuilder.create(tableName, body, primaryKeyField, primaryKeyType)
    val preparedStatement = conn.prepareStatement(sql)

    if(primaryKeyType == "UUID")  preparedStatement.setObject(1, UUID.randomUUID())
    // TODO: If can figure out how to set DEFAULT here, then won't need to pass primaryKeyType into QueryBuilder.create
    // eg. NULL works in MySQL to generate default value, but not Postgres unfortunately
    else preparedStatement.setNull(1, java.sql.Types.TIMESTAMP) // would probably work for MySQL
    // Or if this worked: preparedStatement.setDefault(1, java.sql.Types.DEFAULT) // but no such type as DEFAULT
    // Want to replicate this query: INSERT INTO test_serial (id) VALUES(DEFAULT)

    // Instead for now send primaryKeyType to QueryBuilder.create, where it handles this and sets id = DEFAULT
    // instead of using ? parameter
    // no need to change loop below because Primary key is handled

    for (i <- 2 to body.size + 1) {
      val value = body(body.keys.toList(i - 2))
      preparedStatement.setObject(i, value)
    }
    val _ = preparedStatement.executeUpdate()
  }

  def update(
      tableName: String,
      fieldsAndValues: Map[TableColumn, String],
      primaryKeyField: String,
      primaryKeyValue: String
  )(implicit conn: Connection): Unit = {
    val sql = QueryBuilder.update(tableName, fieldsAndValues, primaryKeyField)
    val preparedStatement = conn.prepareStatement(sql)

    val notNullData = fieldsAndValues.filterNot { case (_, value) => value == "null" }
    notNullData.zipWithIndex.foreach { case ((_, value), i) =>
      preparedStatement.setObject(i + 1, value)
    }
    // where ... = ?
    preparedStatement.setObject(notNullData.size + 1, UUID.fromString(primaryKeyValue))
    val _ = preparedStatement.executeUpdate()
  }

  def delete(tableName: String, primaryKeyField: String, primaryKeyValue: String)(implicit
      conn: Connection
  ): Unit = {
    val sql = s"""
      DELETE FROM $tableName
      WHERE $primaryKeyField = ?
      """
    val preparedStatement = conn.prepareStatement(sql)

    preparedStatement.setObject(1, UUID.fromString(primaryKeyValue))
    val _ = preparedStatement.executeUpdate()
  }

  def countRecordsOnTable(tableName: String)(implicit conn: Connection): Int = {
    SQL"""
      SELECT COUNT(*)
      FROM #$tableName
      """.as(SqlParser.int("count").single)
  }
}
