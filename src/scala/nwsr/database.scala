package com.aquamentis.nwsr

import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.preference.PreferenceManager

import scala.math.pow
import scala.util.Random

import com.aquamentis.util.Feed
import com.aquamentis.util.Story
import com.aquamentis.util.RichDatabase._

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

  val createDomains = ("create table domain (" +
                       "repr string primary key, " +
                       "positive integer, " +
                       "negative integer);")

  val createWords = ("create table word (" +
                     "repr string primary key, " +
                     "positive integer, " +
                     "negative integer);")

  val createBigrams = ("create table bigram (" +
                       "repr string primary key, " +
                       "positive integer, " +
                       "negative integer);")

  val createSeen = ("create table seen (" +
                    "title integer primary key, " +
                    "updated integer);")

  val createSaved = ("create table saved (" +
                     "_id integer primary key, " +
                     "title string, " +
                     "link string);")

  var helper: Option[SQLiteOpenHelper] = None

  def apply(context: Context): SQLiteOpenHelper = synchronized {
    helper match {
      case Some(h) => h
      case None => {
        val h = new SQLiteOpenHelper(context, name, null, version) {
          override def onCreate(db: SQLiteDatabase) {
            db.exclusiveTransaction {
              db.execSQL(createStories)
              db.execSQL(createFeeds)
              db.execSQL(createDomains)
              db.execSQL(createWords)
              db.execSQL(createBigrams)
              db.execSQL(createSeen)
              db.execSQL(createSaved)
            }
          }

          override def onUpgrade(db: SQLiteDatabase, oldVer: Int,
                                 newVer: Int) {
            // Remove trigram table in next version of database
          }
        }
        helper = Some(h)
        h
      }
    }
  }
}


object NWSRDatabase {
  def apply(context: Context): NWSRDatabase = {
    new NWSRDatabase(NWSRDatabaseHelper(context).getWritableDatabase(),
                     PreferenceManager.getDefaultSharedPreferences(context))
  }
}

class NWSRDatabase (val db: SQLiteDatabase, val prefs: SharedPreferences)
extends Classifier {
  val rng: Random = new Random()

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

  def feedsToRefresh(req: FeedRequest): Cursor = req match {
    case FeedLink(link: String) => db.rawQuery(
      "select null", Array.empty[String])
    case FeedId(id: Long) => db.rawQuery(
      "select _id, link, etag, last_modified from feed where _id = %d"
      .format(id), Array.empty[String])
    case FeedAll => db.query(
      "feed", Array("_id", "link", "etag", "last_modified"),
      null, null, null, null, null)
  }


  def addStory(story: Story, id: Long) {
    db.query(
      "select 1 where exists (select null from seen where title = %d)"
      .format(story.title.hashCode())).ifNotExists {
        val values = new ContentValues()
        val seen = new ContentValues()
        val now: Long = System.currentTimeMillis
        values.put("title", story.title)
        seen.put("title", java.lang.Integer.valueOf(story.title.hashCode()))

        // Assume http, https links remain as they are
        values.put("link", story.link.stripPrefix("http://"))

        val cf = classify(story)
        values.put(
          "weight", pow(rng.nextDouble(), if (cf > 0) (1/(cf+1.0)) else 1.0))

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
