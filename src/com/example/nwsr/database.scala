package com.example.nwsr

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

import scala.util.Random

class NWSRDatabaseHelper (val context: Context)
extends SQLiteOpenHelper (context, "NWSR", null, 1) {

  val createStories = ("create table story (" +
                       "_id integer primary key, " +
                       "title string, " +
                       "link string, " +
                       "weight real, " +
                       "updated integer, " +
                       "feed integer references feed" +
                       ");")

  val createFeeds = ("create table feed (" +
                     "_id integer primary key, " +
                     "title string, " +
                     "link string, " +
                     "updated integer, " +
                     "etag string, " +
                     "last_modified string" +
                     ");")
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

  def feeds(): Cursor = db.query("feed", Array("_id", "title"), null, null,
                                 null, null, "title asc")

  def addFeed(title: String, link: String) = {
    val values = new ContentValues()
    val now: Long = System.currentTimeMillis/1000
    values.put("title", title)
    values.put("link", link)

    // This won't compile with the long value in Scala
    values.put("updated", java.lang.Long.valueOf(now))

    db.insert("feed", null, values)
  }

  def deleteFeed(id: Long) {
    db.delete("feed", "_id = " + id, null)
    db.delete("story", "feed = " + id, null)
  }

  def addStory(title: String, link: String, id: Long) = {
    // Bloom filter to determine whether or not story is a dupe
    val values = new ContentValues()
    val now: Long = System.currentTimeMillis/1000
    values.put("title", title)
    // Assume http, https links remain as they are
    values.put("link", link.stripPrefix("http://"))
    // NB filter goes here
    values.put("weight", rng.nextDouble())
    values.put("updated", java.lang.Long.valueOf(now))
    values.put("feed", java.lang.Long.valueOf(id))

    db.insert("story", null, values)
  }

  def stories(): Cursor = db.query(
    "story", Array("_id", "title", "link"), null, null, null,
    null, "weight desc", "20")

/*
Leak found
java.lang.IllegalStateException: /data/data/com.example.nwsr/databases/NWSR SQLiteDatabase created and never closed
*/
}
