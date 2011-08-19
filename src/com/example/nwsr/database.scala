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

  val createWords = ("create table word (" +
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
  val rng: Random = new Random()
  val prefs = PreferenceManager.getDefaultSharedPreferences(context)

  def feeds(): Cursor = db.query("feed", Array("_id", "title", "link"),
                                 null, null, null, null, "title asc")

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
    val cf = classifyStory(title)
    values.put("weight", pow(rng.nextDouble(), cf._2/(cf._1+cf._2)))
    values.put("updated", java.lang.Long.valueOf(now))
    values.put("feed", java.lang.Long.valueOf(id))

    db.insert("story", null, values)
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

  def classifyStory(title: String): (Double, Double) = {
    val posHeadlines = prefs.getLong("positive_headline_count", 0)
    val negHeadlines = prefs.getLong("negative_headline_count", 0)
    val totHeadlines: Double = posHeadlines + negHeadlines
    val totWords = {
      val cWord = db.rawQuery("select count(*) from word", Array.empty[String])
      cWord.moveToFirst()
      val result = cWord.getLong(0)
      cWord.close()
      result
    }
    val posDenom: Double = 1.0/(prefs.getLong("positive_word_count", 0) + totWords)
    val negDenom: Double = 1.0/(prefs.getLong("negative_word_count", 0) + totWords)
    var positive: Double = posHeadlines / totHeadlines
    var negative: Double = negHeadlines / totHeadlines
    for {
      word <- punctuation.replaceAllIn(title, " ")
        .toLowerCase().split(' ') 
      if word.length > 2 && !commonWords.contains(word)
    } {
      val cWord = db.query(
        "word", Array("positive", "negative"),
        "repr = ?", Array(word), null, null, null)
      if (cWord.getCount > 0) {
        cWord.moveToFirst()
        positive = positive * posDenom * (cWord.getLong(0) + 1)
        negative = negative * negDenom * (cWord.getLong(1) + 1)
      }
      cWord.close()
    }
    (positive, negative)
  }

  def filterStory(id: Long, positive: Boolean) {
    val cStory = db.query("story", Array("title"), "_id = ?",
                         Array(id.toString), null, null, null)
    cStory.moveToFirst()
    val editor = prefs.edit()
    val countKey = if (positive) "positive_headline_count"
                   else "negative_headline_count"
    editor.putLong(countKey, prefs.getLong(countKey, 0) + 1)
    val words = HashMap.empty[String, Int]
    for {
      word <- punctuation.replaceAllIn(cStory.getString(0), " ")
        .toLowerCase().split(' ') 
      if word.length > 2 && !commonWords.contains(word)
    } {
      if (words.contains(word))
        words(word) = words(word) + 1
      else words(word) = 1
    }
    for (word <- words.keySet) {
      val cWord = db.query(
        "word", Array("_id", if (positive) "positive" else "negative"),
        "repr = ?", Array(word), null, null, null)
      if (cWord.getCount > 0) {
        cWord.moveToFirst()
        val values = new ContentValues()
        values.put(if (positive) "positive" else "negative", 
                   java.lang.Long.valueOf(cWord.getLong(1) + words(word)))
        db.update("word", values, "_id = ?", Array(cWord.getLong(0).toString))
      } else {
        val values = new ContentValues()
        values.put("repr", word)
        values.put(if (positive) "positive" else "negative",
                   java.lang.Long.valueOf(words(word)))
        values.put(if (positive) "negative" else "positive",
                   java.lang.Long.valueOf(0))
        db.insert("word", null, values)
        val vocabKey = if (positive) "positive_word_count"
                       else "negative_word_count"
        editor.putLong(vocabKey, prefs.getLong(vocabKey, 0) + 1)
      }
      cWord.close()
    }
    editor.commit()
    cStory.close()
    db.delete("story", "_id = " + id, null)
  }

  def stories(): Cursor = db.query(
    "story", Array("_id", "title", "link"), null, null, null,
    null, "weight desc", "20")

  def close() {
    helper.close()
  }
}
