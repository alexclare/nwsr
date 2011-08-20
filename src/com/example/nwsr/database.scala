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
                       "pos real, neg real, " +
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
    // Deleting the story here might conflict with the bloom filter idea
    db.delete("story", "feed = " + id, null)
  }

  def stories(): Cursor = db.query(
    "story", Array("_id", "title", "link", "pos", "neg"), null, null, null,
    null, "weight desc", "20")

  def addStory(title: String, link: String, id: Long) = {
    // Bloom filter to determine whether or not story is a dupe
    val values = new ContentValues()
    val now: Long = System.currentTimeMillis/1000
    values.put("title", title)
    // Assume http, https links remain as they are
    values.put("link", link.stripPrefix("http://"))
    values.put("weight", rng.nextDouble())

    val cf = classifyStory(title)
    values.put("pos", cf._1/(cf._1+cf._2))
    values.put("neg", cf._2/(cf._1+cf._2))
    // weight here
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

  def titleWords(title: String) = punctuation.replaceAllIn(title, " ")
    .toLowerCase().split(' ')
    .filter((word) => word.length > 2 && !commonWords.contains(word))

  def classifyStory(title: String): (Double, Double) = {
    val posHeadlines = prefs.getLong("positive_headline_count", 0)
    val negHeadlines = prefs.getLong("negative_headline_count", 1)
    val totHeadlines: Double = posHeadlines + negHeadlines
    val totWords = {
      val cWord = db.rawQuery("select count(*) from word", Array.empty[String])
      cWord.moveToFirst()
      val result = cWord.getLong(0)
      cWord.close()
      result
    }
    val posDenom: Double = 1.0/(prefs.getLong("positive_word_count", 0) + totWords)
    val negDenom: Double = 1.0/(prefs.getLong("negative_word_count", 1) + totWords)
    var positive: Double = posHeadlines / totHeadlines
    var negative: Double = negHeadlines / totHeadlines
    for (word <- titleWords(title)) {
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
    val curStories = db.query("story", Array("title"), "_id in (?)",
                              Array(idString), null, null, null)
    foreach(curStories) {
      (story: Cursor) =>
        for (word <- titleWords(story.getString(0))) {
          words(word) = words(word) + 1
        }
    }
    curStories.close()

    val vocabKey = if (positive) "positive_word_count"
                   else "negative_word_count"
    var vocab = prefs.getLong(vocabKey, 0)
    for (word <- words.keySet) {
      val curWord = db.query(
        "word", Array("_id", if (positive) "positive" else "negative"),
        "repr = ?", Array(word), null, null, null)
      if (curWord.getCount > 0) {
        curWord.moveToFirst()
        val values = new ContentValues()
        values.put(if (positive) "positive" else "negative", 
                   java.lang.Long.valueOf(curWord.getLong(1) + words(word)))
        db.update("word", values, "_id = ?",
                  Array(curWord.getLong(0).toString))
      } else {
        val values = new ContentValues()
        values.put("repr", word)
        values.put(if (positive) "positive" else "negative",
                   java.lang.Long.valueOf(words(word)))
        values.put(if (positive) "negative" else "positive",
                   java.lang.Long.valueOf(0))
        db.insert("word", null, values)
        vocab += 1
      }
      curWord.close()
    }

    editor.putLong(vocabKey, vocab)
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
