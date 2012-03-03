package com.aquamentis.util

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase

/** A very simple wrapper around SQLiteDatabase to handle three common types of
 *    raw queries: grabbing information from a single row, conditional
 *    execution based on the presence or absence of a row, and iterating over
 *    the results of a query.
 *
 *  The cursor representing the query is closed before returning to the caller.
 *
 *  Also includes a utility method to wrap around exclusive transaction blocks.
 */

object RichDatabase {
  implicit def EnrichCursor(cur: Cursor) = new RichCursor(cur)
  implicit def EnrichDatabase(db: SQLiteDatabase) = new RichDatabase(db)
}
import RichDatabase._

class RichDatabase(db: SQLiteDatabase) {
  def exclusiveTransaction(fn: => Unit) {
    db.beginTransaction()
    try {
      fn
      db.setTransactionSuccessful()
    } finally {
      db.endTransaction()
    }
  }

  def query(query: String) = new Query(db.rawQuery(query, Array.empty[String]))
}

class RichCursor(cur: Cursor) {
  def foreach(fn: => Unit) {
    cur.moveToFirst()
    while (!cur.isAfterLast) {
      fn
      cur.moveToNext()
    }
  }
}

/** The Query class is designed for one-off database queries; the underlying
 *  cursor is closed after a single use
 */
class Query(val cursor: Cursor) {
  def foreach(fn: (Cursor => Unit)) {
    cursor.foreach {
      fn(cursor)
    }
    cursor.close()
  }

  def singleRow[T](fn: (Cursor => T)) = {
    cursor.moveToFirst()
    val result = fn(cursor)
    cursor.close()
    result
  }

  def ifExists(fn: (Cursor => Unit)): QueryOtherwise = {
    var qo: QueryOtherwise = new QueryOtherwiseFunction()
    if (cursor.getCount > 0) {
      cursor.moveToFirst()
      fn(cursor)
      qo = new QueryOtherwiseNothing()
    }
    cursor.close()
    qo
  }

  def ifNotExists(fn: => Unit) {
    if (cursor.getCount <= 0) {
      fn
    }
    cursor.close()
  }
}

trait QueryOtherwise {
  def otherwise(fn: => Unit)
}

class QueryOtherwiseFunction extends QueryOtherwise {
  def otherwise(fn: => Unit) {
    fn
  }
}

class QueryOtherwiseNothing extends QueryOtherwise {
  def otherwise(fn: => Unit) { }
}
