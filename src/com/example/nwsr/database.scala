package com.example.nwsr

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class NWSRDatabaseHelper (val context: Context)
extends SQLiteOpenHelper (context, "NWSR", null, 1) {
  val createStories = ("create table stories (" +
                      "_id integer primary key, " +
                      "title string, " +
                      "hash_title integer, " +
                      "link string, " +
                      "updated integer);")

  val createFeeds = ("create table feeds (" +
                    "_id integer primary key, " +
                    "title string, " +
                    "link string, " +
                    "updated integer, " +
                    "etag string, " +
                    "last_modified string);")

  val createWords = ("create table words (" +
                    "_id integer primary key, " +
                    "repr string unique, " +
                    "positive integer, " +
                    "negative integer);")

  override def onCreate(db: SQLiteDatabase) {
    db.execSQL(createStories)
    db.execSQL(createFeeds)
    db.execSQL(createWords)
  }

  override def onUpgrade(db: SQLiteDatabase, oldVer: Int, newVer: Int) {
    // Add code to update the database when version 2 comes along
  }
}

class NWSRDatabase (context: Context) {
  var helper: NWSRDatabaseHelper = new NWSRDatabaseHelper(context)
  var db: SQLiteDatabase = helper.getWritableDatabase()

  def feeds(): Cursor = db.query("feeds", Array("_id", "title"), null, null,
                                 null, null, "title asc")

  def addFeed(url: String) = {
    val values = new ContentValues()
    val now: Long = System.currentTimeMillis()/1000
    values.put("title", url)
    values.put("link", url)
    // It won't compile with the long value in Scala, must be a bug
    values.put("updated", java.lang.Long.valueOf(now))
    db.insert("feeds", null, values)
  }

  def deleteFeed(id: Long) {
    db.delete("feeds", "_id = " + id, null)
  }
}