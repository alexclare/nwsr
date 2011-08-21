package com.example.nwsr

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.preference.PreferenceManager

import scala.collection.mutable.HashMap
import scala.math.pow
import scala.util.Random

// The ContentValues.put functions seem to require "java.lang.Type.valueOf"
//   calls to appease the type checker

object NWSRDatabaseHelper {
  val name = "nwsr.db"
  val version = 1

  val createStories = ("create table story (" +
                       "_id integer primary key, " +
                       "title string, " +
                       "link string, " +
                       "weight real, " +
                       "pos real, neg real, " +
                       "updated integer, " +
                       "feed integer references feed);")

  val createFeeds = ("create table feed (" +
                     "_id integer primary key, " +
                     "title string, " +
                     "link string, " +
                     "display_link string," +
                     "updated integer, " +
                     "etag string, " +
                     "last_modified string);")

  val createWords = ("create table word (" +
                    "_id integer primary key, " +
                    "repr string unique, " +
                    "positive integer, " +
                    "negative integer);")

  val createSeen = ("create table seen (" +
                    "title integer primary key, " +
                    "updated integer);")
}

class NWSRDatabaseHelper (val context: Context)
extends SQLiteOpenHelper (context, NWSRDatabaseHelper.name, null,
                          NWSRDatabaseHelper.version) {
  import NWSRDatabaseHelper._

  override def onCreate(db: SQLiteDatabase) {
    db.execSQL(createStories)
    db.execSQL(createFeeds)
    db.execSQL(createWords)
    db.execSQL(createSeen)
  }

  override def onUpgrade(db: SQLiteDatabase, oldVer: Int, newVer: Int) {
    // Add code to update the database when version 2 comes along
  }
}

class NWSRDatabase (context: Context) {
  var helper: NWSRDatabaseHelper = _
  var db: SQLiteDatabase = _
  val rng: Random = new Random()
  val prefs = PreferenceManager.getDefaultSharedPreferences(context)

  def open(): NWSRDatabase = {
    helper = new NWSRDatabaseHelper(context)
    db = helper.getWritableDatabase()
    this
  }

  def feeds(): Cursor = db.query("feed", Array("_id", "title", "display_link"),
                                 null, null, null, null, "title asc")

  def addFeed(title: String, link: String, displayLink: String,
              etag: Option[String], lastModified: Option[String]) = {
    val values = new ContentValues()
    val now: Long = System.currentTimeMillis/1000
    values.put("title", title)
    values.put("link", link)
    values.put("display_link", displayLink)
    values.put("updated", java.lang.Long.valueOf(now))

    etag match {
      case Some(e) => values.put("etag", e)
      case None =>
    }
    lastModified match {
      case Some(lm) => values.put("last_modified", lm)
      case None =>
    }
    db.insert("feed", null, values)
  }

  def deleteFeed(id: Long) {
    db.delete("feed", "_id = " + id, null)
    // Deleting the story here might conflict with the bloom filter idea
    db.delete("story", "feed = " + id, null)
  }
/*
  def refreshFeeds() {
    // give it every id
    val curFeeds = db.query("feed", Array("_id"), null, null, null, null, null)
    foreach(curFeeds) {
      refreshFeed(curFeeds.getLong(0))
    }
    curFeeds.close()
  }

  def refreshFeeds(ids: Array[Long]) {
    val curFeeds = db.query("feed", Array("_id"), null, null, null, null, null)
  }
*/
  def stories(): Cursor = db.query(
    "story", Array("_id", "title", "link", "pos", "neg"), null, null, null,
    null, "weight desc", "20")

  def addStory(title: String, link: String, id: Long) = {
    val cur = db.rawQuery(
      "select 1 where exists (select null from seen where title = %d)"
      .format(title.hashCode()),
      Array.empty[String])
    if (cur.getCount <= 0) {
      val story = new ContentValues()
      val seen = new ContentValues()
      val now: Long = System.currentTimeMillis/1000
      story.put("title", title)
      seen.put("title", java.lang.Integer.valueOf(title.hashCode()))

      // Assume http, https links remain as they are
      story.put("link", link.stripPrefix("http://"))

      story.put("weight", rng.nextDouble())
      // real weighting algorithm here
      val cf = classifyStory(title)
      story.put("pos", cf._1/(cf._1+cf._2))
      story.put("neg", cf._2/(cf._1+cf._2))

      story.put("updated", java.lang.Long.valueOf(now))
      seen.put("updated", java.lang.Long.valueOf(now))
      story.put("feed", java.lang.Long.valueOf(id))

      db.insert("story", null, story)
      db.insert("seen", null, seen) 
    }
    cur.close()
  }

  val punctuation = "\\p{Punct}+".r
  val commonWords = Set(
    "the","and","that","have","for","not","with","you","this",
    "but","his","from","they","say","her","she","will","one","all","would",
    "there","their","what","out","about","who","get","which","when","make",
    "can","like","time","just","him","know","take","person","into","year",
    "your","good","some","could","them","see","other","than","then","now",
    "look","only","come","its","over","think","also","back","after","use",
    "two","how","our","work","first","well","way","even","new","want",
    "because","any","these","give","day","most")

  def normalizeWords(title: String) = punctuation.replaceAllIn(title, " ")
    .toLowerCase().split(' ')
    .filter((word) => word.length > 2 && !commonWords.contains(word))

  def classifyStory(title: String): (Double, Double) = {
    val posDocs = prefs.getLong("positive_headline_count", 0)
    val negDocs = prefs.getLong("negative_headline_count", 0)
    val totDocs: Double = (posDocs + negDocs).toDouble max 1e-5
    val totWords = {
      val cWord = db.rawQuery("select count(*) from word", Array.empty[String])
      cWord.moveToFirst()
      val result = cWord.getLong(0)
      cWord.close()
      result
    }
    val posDenom: Double = {
      val cWord = db.rawQuery(
        "select count(*) from word where positive > 0", Array.empty[String])
      cWord.moveToFirst()
      val result = cWord.getLong(0)
      cWord.close()
      (result + totWords).toDouble
    }
    val negDenom: Double = {
      val cWord = db.rawQuery(
        "select count(*) from word where negative > 0", Array.empty[String])
      cWord.moveToFirst()
      val result = cWord.getLong(0)
      cWord.close()
      (result + totWords).toDouble
    }
    var positive: Double = posDocs / totDocs
    var negative: Double = negDocs / totDocs
    for (word <- normalizeWords(title)) {
      val cWord = db.query(
        "word", Array("positive", "negative"),
        "repr = ?", Array(word), null, null, null)
      if (cWord.getCount > 0) {
        cWord.moveToFirst()
        positive *= ((cWord.getLong(0) + 1)/posDenom)
        negative *= ((cWord.getLong(1) + 1)/negDenom)
      }
      cWord.close()
    }
    (positive, negative)
  }

  def foreach(cursor: Cursor)(fn: (Cursor => Unit)) {
    cursor.moveToFirst()
    while (!cursor.isAfterLast) {
      fn(cursor)
      cursor.moveToNext()
    }
  }

  def filterStories(ids: Array[Long], positive: Boolean) {
    val editor = prefs.edit()
    val words = new HashMap[String, Int]() {
      override def default(key: String): Int = 0
    }
    val idString = ids.mkString(", ")
    val curStories = db.rawQuery(
      "select title from story where _id in (" + idString + ")",
      Array.empty[String])
    foreach(curStories) {
      (story: Cursor) =>
        for (word <- normalizeWords(story.getString(0))) {
          words(word) = words(word) + 1
        }
    }
    curStories.close()

    for (word <- words.keySet) {
      val curWord = db.query(
        "word", Array("_id", if (positive) "positive" else "negative"),
        "repr = ?", Array(word), null, null, null)
      val values = new ContentValues()
      if (curWord.getCount > 0) {
        curWord.moveToFirst()
        values.put(if (positive) "positive" else "negative", 
                   java.lang.Long.valueOf(curWord.getLong(1) + words(word)))
        db.update("word", values, "_id = ?",
                  Array(curWord.getLong(0).toString))
      } else {
        values.put("repr", word) // truncates leading 0s on numbers!
        values.put(if (positive) "positive" else "negative",
                   java.lang.Long.valueOf(words(word)))
        values.put(if (positive) "negative" else "positive",
                   java.lang.Long.valueOf(0))
        db.insert("word", null, values)
      }
      curWord.close()
    }

    val headlineKey = if (positive) "positive_headline_count"
                      else "negative_headline_count"
    editor.putLong(headlineKey, prefs.getLong(headlineKey, 0) + ids.length)
    editor.commit()

    db.execSQL("delete from story where _id in (" + idString + ")")
  }

  def close() {
    helper.close()
  }
}
