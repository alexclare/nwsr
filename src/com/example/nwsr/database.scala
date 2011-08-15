package com.example.nwsr

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

import scala.util.Random

class NWSRDatabaseHelper (val context: Context)
extends SQLiteOpenHelper (context, "NWSR", null, 1) {

  val createStories = ("create table stories (" +
                      "_id integer primary key, " +
                      "title string, " +
                      "link string, " +
                      "weight real, " +
                      "updated integer);")

  val createFeeds = ("create table feeds (" +
                    "_id integer primary key, " +
                    "title string, " +
                    "link string, " +
                    "updated integer, " +
                    "etag string, " +
                    "last_modified string);")
/*
  val createWords = ("create table words (" +
                    "_id integer primary key, " +
                    "repr string unique, " +
                    "positive integer, " +
                    "negative integer);")
*/
  override def onCreate(db: SQLiteDatabase) {
    db.execSQL(createStories)
    db.execSQL(createFeeds)
    //db.execSQL(createWords)
  }

  override def onUpgrade(db: SQLiteDatabase, oldVer: Int, newVer: Int) {
    // Add code to update the database when version 2 comes along
  }
}

class NWSRDatabase (context: Context) {
  var helper: NWSRDatabaseHelper = new NWSRDatabaseHelper(context)
  var db: SQLiteDatabase = helper.getWritableDatabase()
  val rng: Random = new Random()

  def feeds(): Cursor = db.query("feeds", Array("_id", "title"), null, null,
                                 null, null, "title asc")

  def addFeed(title: String, link: String) = {
    val values = new ContentValues()
    val now: Long = System.currentTimeMillis/1000
    values.put("title", title)
    values.put("link", link)

    // This won't compile with the long value in Scala
    values.put("updated", java.lang.Long.valueOf(now))

    db.insert("feeds", null, values)
  }

  def deleteFeed(id: Long) {
    db.delete("feeds", "_id = " + id, null)
  }

  def addStory(title: String, link: String) = {
    // Bloom filter to determine whether or not story is a dupe
    val values = new ContentValues()
    val now: Long = System.currentTimeMillis/1000
    values.put("title", title)
    values.put("link", link)
    // NB filter goes here
    values.put("weight", rng.nextDouble())
    values.put("updated", java.lang.Long.valueOf(now))

    db.insert("stories", null, values)
  }

  def stories(): Cursor = db.query(
    "stories", Array("_id", "title", "link"), null, null, null,
    null, "weight desc")

/*
Leak found
java.lang.IllegalStateException: /data/data/com.example.nwsr/databases/NWSR SQLiteDatabase created and never closed
*/
}
