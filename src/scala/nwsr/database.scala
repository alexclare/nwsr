package com.aquamentis.nwsr

import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.preference.PreferenceManager

import scala.collection.immutable.SortedSet
import scala.math.pow
import scala.util.Random

import com.aquamentis.util.Feed
import com.aquamentis.util.Story
import com.aquamentis.util.RichDatabase._

object NWSRDatabaseHelper {
  val name = "nwsr.db"
  val version = 6

  val createStories = ("create table story (" +
                       "_id integer primary key, " +
                       "title string, " +
                       "title_hash blob, " +
                       "link string, " +
                       "weight real, " +
                       "updated integer, " +
                       "status integer, " +
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
                       "repr1 string, " +
                       "repr2 string, " +
                       "positive integer, " +
                       "negative integer, " +
                       "primary key (repr1, repr2));")

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
            }
          }

          override def onUpgrade(db: SQLiteDatabase, oldVer: Int,
                                 newVer: Int) {
            if (oldVer == 4) {
              db.exclusiveTransaction {
                db.execSQL("drop table trigram;")
                db.execSQL("drop table bigram;")
                db.execSQL(createBigrams)
              }
            }
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
    db.query("story", Array("_id", "title", "link"),
             "status = 1", null, null, null, "weight desc", limit)
  }

  def feedView(): Cursor = db.query(
    "feed", Array("_id", "title", "display_link"),
    null, null, null, null, "title asc")

  def savedView(): Cursor = db.query(
    "story", Array("_id", "title", "link"),
    "status = 2", null, null, null, "updated desc")


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
    val hash = titleHash(story)
    // Assume http, https links remain as they are
    val link = story.link.stripPrefix("http://")

/*
 * For the coming update to util/database and classifier:
 Query(db.rawQuery("select 1 where exists (select null from story where title_hash = ? and link = ?)", Array(hash, link)))
*/
    db.query("select 1 where exists (select null from story where title_hash = '%s' and link = '%s')"
             .format(hash, link))
    .ifNotExists {
      val values = new ContentValues()
      val now: Long = System.currentTimeMillis
      values.put("title", story.title)
      values.put("title_hash", hash)
      values.put("link", link)

/*
      Query(db.query(
        "story", Array("weight"), "title_hash = ?",
        Array(hash), null, null))
*/   
      db.query("select weight from story where title_hash = '%s'".format(hash))
      .ifExists {
        (c: Cursor) => values.put("weight", c.getDouble(0))
      } otherwise {
        val cf = classify(story)
        values.put(
          "weight", pow(rng.nextDouble(), if (cf > 0) (1/(cf+1.0)) else 1.0))
      }

      values.put("updated", java.lang.Long.valueOf(now))
      values.put("status", java.lang.Integer.valueOf(1))
      values.put("feed", java.lang.Long.valueOf(id))

      db.insert("story", null, values)        
    }
  }

  /** Construct a hash value for the story by concatenating the smallest
   *    3 hash values of character trigrams of the title.
   *
   *  There are faster ways to find the minimum 3 than sorting, but we'll
   *    never have thousands of items to sift through.
   */
  def titleHash(story: Story): String = {
    val processed = story.title.toLowerCase
    "%08x%08x%08x".format(
      SortedSet(processed.sliding(3).map(_.hashCode).toSeq:_*)
      .take(3).toSeq:_*)
  }

  def hideStories(ids: Array[Long]) {
    val values = new ContentValues()
    values.put("status", java.lang.Integer.valueOf(0))
    db.update("story", values, "_id in (%s)".format(ids.mkString(", ")), null)
  }

  def purgeOld() {
    val timeAgo: Long = System.currentTimeMillis -
      prefs.getString("max_story_age", "604800000").toLong
    db.delete("story", "updated < ? and status = 0 or 1",
              Array(timeAgo.toString))
  }

  def addSaved(id: Long) {
    val values = new ContentValues()
    values.put("status", java.lang.Integer.valueOf(2))
    values.put("updated", java.lang.Long.valueOf(System.currentTimeMillis))
    db.update("story", values, "_id = ?", Array(id.toString))
  }

  def deleteSaved(id: Option[Long]) {
    val values = new ContentValues()
    values.put("status", java.lang.Integer.valueOf(0))
    id match {
      case None => db.update("story", values, "status = 2", null)
      case Some(id) => db.update(
        "story", values, "_id = ?",Array(id.toString))
    }
  }
}
