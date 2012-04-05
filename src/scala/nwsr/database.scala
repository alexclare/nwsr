package com.aquamentis.nwsr

import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.preference.PreferenceManager

import scala.collection.immutable.SortedSet
import scala.math.exp
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

  // Status = 0 for hidden, 1 for "headlines", 2 for "saved"
                       "status integer, " +
                       "feed integer references feed);")

  val createFeeds = ("create table feed (" +
                     "_id integer primary key, " +
                     "title string, " +
                     "link string, " +
                     "display_link string, " +
                     "etag string, " +
                     "last_modified string);")

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
            }
          }

          override def onUpgrade(db: SQLiteDatabase, oldVer: Int,
                                 newVer: Int) {
            if (oldVer == 5) {
              db.exclusiveTransaction {
                db.execSQL("drop table story")
                db.execSQL(createStories)
                db.execSQL("drop table saved;")
                db.execSQL("drop table bigram;")
                db.execSQL("drop table word;")
                db.execSQL("drop table domain;")
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

  /** Construct a hash value for the story by concatenating the smallest
   *    3 hash values of character trigrams of the title.
   *
   *  There are faster ways to find the minimum 3 than sorting, but we'll
   *    never have thousands of items to sift through.
   */
  def titleHash(story: Story): String = {
    val processed = story.title.toLowerCase
    "%08x%08x%08x".format(
      (SortedSet(processed.sliding(3).map(_.hashCode).toSeq:_*) ++
       Seq(Int.MaxValue, Int.MaxValue-1, Int.MaxValue-2))
      .take(3).toSeq:_*)
  }
}

class NWSRDatabase (val db: SQLiteDatabase, val prefs: SharedPreferences) {
  import NWSRDatabase._
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

    /* This might be a good place for a wrapped transaction, but addStory
     *   involves a lot of processing for the transaction to be exclusive
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

    db.query("select 1 where exists (select null from story where title_hash = '%s' and link = '%s')"
             .format(hash, link))
    .ifNotExists {
      val values = new ContentValues()
      values.put("title", story.title)
      values.put("title_hash", hash)
      values.put("link", link)

      values.put("weight", rng.nextDouble())
      values.put("updated", java.lang.Long.valueOf(System.currentTimeMillis))
      db.query("select weight, updated, status from story where title_hash = '%s' order by status asc".format(hash))
      .ifExists {
        (c: Cursor) =>
          val status = c.getInt(2)
          if (status == 0 || status == 1) {
            values.put("weight", c.getDouble(0))
            values.put("updated", java.lang.Long.valueOf(c.getLong(1)))
          }
      }

      values.put("status", java.lang.Integer.valueOf(1))
      values.put("feed", java.lang.Long.valueOf(id))

      db.insert("story", null, values)        
    }
  }

  def hideStories(ids: Array[Long]) {
    val values = new ContentValues()
    values.put("status", java.lang.Integer.valueOf(0))
    db.update("story", values, "_id in (%s)".format(ids.mkString(", ")), null)
  }

  def purgeOld() {
    val rate: Double = prefs.getString("story_decay_rate", "8.88343e-09").toDouble
    val now = System.currentTimeMillis
    db.exclusiveTransaction {
      db.query("select _id, weight, updated from story where status = 1").foreach {
        (c: Cursor) =>
          val timeAgo = now - c.getLong(2)
          if (timeAgo > 3600000) {
            val values = new ContentValues()
            values.put("weight", c.getDouble(1) * exp(-1.0*rate*timeAgo))
            values.put("updated", java.lang.Long.valueOf(now))
            db.update("story", values, "_id = ?",
                      Array[String](c.getLong(0).toString))
          }
      }
    }

    val stories = db.singleLongQuery("select count(*) from story where not status = 2")
    // Hard limit of 10000 stories for now, maybe make it a preference later
    if (stories > 10000) {
      db.execSQL("delete from story where _id in (select _id from story order by weight asc limit %d)"
                 .format(stories - 10000))
    }
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
