package com.example.util

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase

/** A very simple wrapper around SQLiteDatabase to handle three common types of
 *    raw queries: grabbing information from a single row, conditional
 *    execution based on the presence or absence of a row, and iterating over
 *    the results of a query.
 *
 * The cursor representing the query is closed before returning to the caller.
 */

object RichDatabase {
  implicit def EnrichDatabase(db: SQLiteDatabase) = new RichDatabase(db)
}

class RichDatabase(db: SQLiteDatabase) {
  def singleRow[T](query: String)(fn: (Cursor => T)) = {
    val cursor = db.rawQuery(query, Array.empty[String])
    cursor.moveToFirst()
    val result = fn(cursor)
    cursor.close()
    result
  }

  def conditional(query: String)
  (exists: (Cursor => Unit) = { (c:Cursor) => })
  (otherwise: () => Unit = { () => }) {
    val cursor = db.rawQuery(query, Array.empty[String])
    if (cursor.getCount > 0) {
      cursor.moveToFirst()
      exists(cursor)
    } else {
      otherwise()
    }
    cursor.close()
  }

  def foreach(query: String)(fn: (Cursor => Unit)) {
    val cursor = db.rawQuery(query, Array.empty[String])
    cursor.moveToFirst()
    while (!cursor.isAfterLast) {
      fn(cursor)
      cursor.moveToNext()
    }
    cursor.close()
  }
}
