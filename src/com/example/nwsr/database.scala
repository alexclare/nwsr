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
    Query.conditional(
      "select 1 where exists (select null from seen where title = %d)"
      .format(title.hashCode())) () { () =>
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
    val totWords = Query.singleRow[Long](
      "select count(*) from word")(_.getLong(0))
    val posDenom = Query.singleRow[Double](
      "select count(*) from word where positive > 0")(_.getLong(0) + totWords)
    val negDenom = Query.singleRow[Double](
      "select count(*) from word where negative > 0")(_.getLong(0) + totWords)
    var positive: Double = posDocs / totDocs
    var negative: Double = negDocs / totDocs
    for (word <- normalizeWords(title)) {
      Query.conditional(
        "select positive, negative from word where repr = '%s'"
        .format(word)) {
          (c: Cursor) =>
            positive *= ((c.getLong(0) + 1)/posDenom)
            negative *= ((c.getLong(1) + 1)/negDenom)
        }()
    }
    (positive, negative)
  }

  def filterStories(ids: Array[Long], positive: Boolean) {
    val editor = prefs.edit()
    val words = new HashMap[String, Int]() {
      override def default(key: String): Int = 0
    }
    val idString = ids.mkString(", ")
    val thisClass = if (positive) "positive" else "negative"
    val otherClass = if (positive) "negative" else "positive"
    Query.foreach(
      "select title from story where _id in (%s)".format(idString)) {
      (story: Cursor) =>
        for (word <- normalizeWords(story.getString(0))) {
          words(word) = words(word) + 1
        }
    }

    for (word <- words.keySet) {
      val values = new ContentValues()
      Query.conditional(
        "select _id, %s from word where repr = '%s'"
        .format(thisClass, word)) {
          (c: Cursor) =>
          values.put(thisClass,
                     java.lang.Long.valueOf(c.getLong(1) + words(word)))
          db.update("word", values, "_id = ?",
                    Array(c.getLong(0).toString))
        } { () =>
          values.put("repr", word) // truncates leading 0s on numbers!
          values.put(thisClass, java.lang.Long.valueOf(words(word)))
          values.put(otherClass, java.lang.Long.valueOf(0))
          db.insert("word", null, values)
        }
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

  object Query {
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
}
