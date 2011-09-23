package com.aquamentis.nwsr

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.preference.PreferenceManager

import scala.collection.mutable.HashMap
import scala.collection.mutable.ListBuffer
import scala.math.pow
import scala.util.Random

import com.aquamentis.util.Feed
import com.aquamentis.util.Story
import com.aquamentis.util.RichDatabase._

/* General note: the ContentValues.put functions seem to require
 *   "java.lang.Type.valueOf" calls on occasion to appease the type checker
 */

object NWSRDatabaseHelper {
  val name = "nwsr.db"
  val version = 2

  val createStories = ("create table story (" +
                       "_id integer primary key, " +
                       "title string, " +
                       "link string, " +
                       "weight real, " +
                       "updated integer, " +
                       "show integer, " +
                       "feed integer references feed);")

  val createFeeds = ("create table feed (" +
                     "_id integer primary key, " +
                     "title string, " +
                     "link string, " +
                     "display_link string, " +
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

  val createSaved = ("create table saved (" +
                     "_id integer primary key, " +
                     "title string, " +
                     "link string);")
}

class NWSRDatabaseHelper (val context: Context)
extends SQLiteOpenHelper (context, NWSRDatabaseHelper.name, null,
                          NWSRDatabaseHelper.version) {
  import NWSRDatabaseHelper._

  override def onCreate(db: SQLiteDatabase) {
    db.exclusiveTransaction {
      db.execSQL(createStories)
      db.execSQL(createFeeds)
      db.execSQL(createWords)
      db.execSQL(createSeen)
      db.execSQL(createSaved)
    }
  }

  override def onUpgrade(db: SQLiteDatabase, oldVer: Int, newVer: Int) {
    if (oldVer == 1) {
      db.exclusiveTransaction {
        // drop a few columns from story and one from feed between 0.1.0 and 0.2.x
        db.execSQL("alter table story rename to old_story")
        db.execSQL(createStories)
        db.foreach("select _id, title, link, weight, updated, feed from old_story") {
          (c: Cursor) =>
          val values = new ContentValues()
          values.put("_id", java.lang.Long.valueOf(c.getLong(0)))
          values.put("title", c.getString(1))
          values.put("link", c.getString(2))
          values.put("weight", c.getDouble(3))
          values.put("updated", java.lang.Long.valueOf(c.getLong(4)))
          values.put("show", java.lang.Integer.valueOf(1))
          values.put("feed", java.lang.Long.valueOf(c.getLong(5)))
          db.insert("story", null, values)
        }
        db.execSQL("drop table old_story")

        db.execSQL("alter table feed rename to old_feed")
        db.execSQL(createFeeds)
        db.foreach("select _id, title, link, display_link, etag, last_modified from old_feed") {
          (c: Cursor) =>
          val values = new ContentValues()
          values.put("_id", java.lang.Long.valueOf(c.getLong(0)))
          values.put("title", c.getString(1))
          values.put("link", c.getString(2))
          values.put("display_link", c.getString(3))
          values.put("etag", c.getString(4))
          values.put("last_modified", c.getString(5))
          db.insert("feed", null, values)
        }
        db.execSQL("drop table old_feed")
      }
    }
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

  def close() {
    helper.close()
  }

  def storyView(): Cursor = {
    val limit = prefs.getString("stories_per_page", "20")
    val query = "select _id, title, link from story where show = 1 " +
                "order by weight desc limit %s"
    db.rawQuery(query.format(limit), Array.empty[String])
  }

  def feedView(): Cursor = db.query(
    "feed", Array("_id", "title", "display_link"),
    null, null, null, null, "title asc")

  def savedView(): Cursor = db.query(
    "saved", Array("_id", "title", "link"),
    null, null, null, null, "_id desc")


  def addFeed(feed: Feed, id: Option[Long]) {
    val values = new ContentValues()
    val now: Long = System.currentTimeMillis
    values.put("title", feed.title)
    values.put("link", feed.link)
    values.put("display_link", feed.displayLink)

    feed.etag match {
      case Some(e) => values.put("etag", e)
      case None =>
    }
    feed.lastMod match {
      case Some(lm) => values.put("last_modified", lm)
      case None =>
    }

    /* This would be a good place for a wrapped transaction, but addStory
     *   involves too much processing (a call to classifyStory) for the
     *   transaction to be exclusive
     */
    val feedId = id match {
      case None => db.insert("feed", null, values)
      case Some(i) => { 
        db.update("feed", values, "_id = " + i, Array.empty[String])
        i
      }
    }
    for (story <- feed.stories) {
      addStory(story, feedId)
    }
  }

  def deleteFeed(id: Long) {
    db.exclusiveTransaction {
      db.delete("story", "feed = " + id, null)
      db.delete("feed", "_id = " + id, null)
    }
  }

  def feedsToRefresh(id: Option[Long]): Cursor = id match {
    case None => db.query(
      "feed", Array("_id", "link", "etag", "last_modified"),
      null, null, null, null, null)
    case Some(id) => db.rawQuery(
      "select _id, link, etag, last_modified from feed where _id = %d"
      .format(id), Array.empty[String])
  }


  def addStory(story: Story, id: Long) {
    db.conditional(
      "select 1 where exists (select null from seen where title = %d)"
      .format(story.title.hashCode())) () {
        // Otherwise clause: add the story if the title hasn't been seen
        val values = new ContentValues()
        val seen = new ContentValues()
        val now: Long = System.currentTimeMillis
        values.put("title", story.title)
        seen.put("title", java.lang.Integer.valueOf(story.title.hashCode()))

        // Assume http, https links remain as they are
        values.put("link", story.link.stripPrefix("http://"))

        val cf = classifyStory(story.title)
        values.put("weight", pow(rng.nextDouble(), cf._2/(cf._1+cf._2)))

        values.put("updated", java.lang.Long.valueOf(now))
        seen.put("updated", java.lang.Long.valueOf(now))

        values.put("show", java.lang.Integer.valueOf(1))
        values.put("feed", java.lang.Long.valueOf(id))

        db.insert("story", null, values)
        db.insert("seen", null, seen) 
      }
  }

  def hideStories(ids: Array[Long]) {
    val values = new ContentValues()
    values.put("show", java.lang.Integer.valueOf(0))
    db.update("story", values, "_id in (%s)".format(ids.mkString(", ")), null)
  }

  def purgeOld() {
    val timeAgo: Long = System.currentTimeMillis -
      prefs.getString("max_story_age", "604800000").toLong
    db.exclusiveTransaction {
      db.execSQL("delete from story where updated < %d".format(timeAgo))
      db.execSQL("delete from seen where updated < %d".format(timeAgo))
    }
  }

  def classifyStory(title: String): (Double, Double) = {
    val posDocs = prefs.getLong("positive_headline_count", 0)
    val negDocs = prefs.getLong("negative_headline_count", 0)
    val totDocs: Double = (posDocs + negDocs).toDouble max 1e-5
    val totWords = db.singleRow[Long](
      "select count(*) from word")(_.getLong(0))
    val posDenom = db.singleRow[Double](
      "select count(*) from word where positive > 0")(_.getLong(0) + totWords)
    val negDenom = db.singleRow[Double](
      "select count(*) from word where negative > 0")(_.getLong(0) + totWords)
    var positive: Double = posDocs / totDocs
    var negative: Double = negDocs / totDocs
    for (word <- Classifier.normalize(title)) {
      db.conditional(
        "select positive, negative from word where repr = '%s'"
        .format(word)) {
          (c: Cursor) =>
            positive *= ((c.getLong(0) + 1)/posDenom)
            negative *= ((c.getLong(1) + 1)/negDenom)
        }()
    }
    (positive, negative)
  }

  /** Adds stories with the given ids to the classifier, belonging to the
   *    class given by storyType
   */
  def trainClassifier(ids: Array[Long], storyType: StoryType) {
    val editor = prefs.edit()
    val words = new HashMap[String, Int]() {
      override def default(key: String): Int = 0
    }
    val idString = ids.mkString(", ")
    val (thisClass, otherClass) = storyType match {
      case PositiveStory => ("positive", "negative")
      case NegativeStory => ("negative", "positive")
    }
    db.foreach(
      "select title from story where _id in (%s)".format(idString)) {
      (story: Cursor) =>
        for (word <- Classifier.normalize(story.getString(0))) {
          words(word) = words(word) + 1
        }
    }

    // Another spot where a wrapped non-exclusive transaction would be helpful
    for (word <- words.keySet) {
      val values = new ContentValues()
      db.conditional(
        "select _id, %s from word where repr = '%s'"
        .format(thisClass, word)) {
          (c: Cursor) =>
            values.put(thisClass,
                       java.lang.Long.valueOf(c.getLong(1) + words(word)))
          db.update("word", values, "_id = ?",
                    Array(c.getLong(0).toString))
        } {
          // The "put" method here truncates leading 0s on string
          //    representations of numbers, e.g. converting "000" to "0"
          values.put("repr", word)
          values.put(thisClass, java.lang.Long.valueOf(words(word)))
          values.put(otherClass, java.lang.Long.valueOf(0))
          db.insert("word", null, values)
        }
    }

    val headlineKey = storyType match {
      case PositiveStory => "positive_headline_count"
      case NegativeStory => "negative_headline_count"
    }
    editor.putLong(headlineKey, prefs.getLong(headlineKey, 0) + ids.length)
    editor.commit()

    db.execSQL("delete from story where _id in (" + idString + ")")
  }


  def addSaved(story: Story) {
    val values = new ContentValues()
    values.put("title", story.title)
    values.put("link", story.link)
    db.insert("saved", null, values)
  }

  def deleteSaved(id: Option[Long]) {
    id match {
      case None => db.delete("saved", null, null)
      case Some(id) => db.delete("saved", "_id = " + id, null)
    }
  }
}
