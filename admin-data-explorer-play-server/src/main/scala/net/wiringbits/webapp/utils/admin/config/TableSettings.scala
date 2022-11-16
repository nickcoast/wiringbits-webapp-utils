package net.wiringbits.webapp.utils.admin.config

import java.util.UUID

/** @param tableName
  *   name of table in database
  * @param primaryKeyField
  *   primary key identifier of table
  * @param referenceField
  *   field that react-admin shows for foreign key references instead of primary key
  * @param hiddenColumns
  *   columns that the API won't return when the data is queried (for example: user password)
  * @param nonEditableColumns
  *   columns that aren't editable (disabled) via react-admin
  * @param canBeDeleted
  *   indicates if resources from this table can be deleted
  * @param primaryKeyDataType
  *   "UUID", or "Int" for SERIAL and BIGSERIAL primary keys
  */

case class TableSettings(
    tableName: String,
    primaryKeyField: String,
    referenceField: Option[String] = None,
    hiddenColumns: List[String] = List.empty,
    nonEditableColumns: List[String] = List.empty,
    canBeDeleted: Boolean = true,
    primaryKeyDataType: String = "UUID"
) {
  /*def isUUIDOrIntOrLongOrString[T: UUIDOrIntOrLongOrString](v: T): String = v match {
    case u: UUID => "UUID"
    case i: Int => "Int"
    case l: Long => "Long"
    case s: String => "String"
  }*/
}

sealed trait UUIDOrIntOrLongOrString[T]
object UUIDOrIntOrLongOrString {
  implicit val UUIDInstance: UUIDOrIntOrLongOrString[UUID] =
    new UUIDOrIntOrLongOrString[UUID] {}
  implicit val IntInstance: UUIDOrIntOrLongOrString[Int] =
    new UUIDOrIntOrLongOrString[Int] {}
  implicit val LongInstance: UUIDOrIntOrLongOrString[Long] =
    new UUIDOrIntOrLongOrString[Long] {}
  implicit val StringInstance: UUIDOrIntOrLongOrString[String] =
    new UUIDOrIntOrLongOrString[String] {}
}