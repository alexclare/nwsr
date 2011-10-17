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

/**
 * A common type that retains a database handle, for functions that can be
 *   called by either activities or intents
 */
trait DatabaseHandle {
  def db: NWSRDatabase
}

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
                       "_id integer primary key, " +
                       "repr string unique, " +
                       "positive integer, " +
                       "negative integer);")

  val createWords = ("create table word (" +
                     "_id integer primary key, " +
                     "repr string unique, " +
                     "positive integer, " +
                     "negative integer);")

  val createBigrams = ("create table bigram (" +
                       "_id integer primary key, " +
                       "repr string unique, " +
                       "positive integer, " +
                       "negative integer);")

  val createTrigrams = ("create table trigram (" +
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
      db.execSQL(createDomains)
      db.execSQL(createWords)
      db.execSQL(createBigrams)
      db.execSQL(createTrigrams)
      db.execSQL(createSeen)
      db.execSQL(createSaved)
    }
  }

  override def onUpgrade(db: SQLiteDatabase, oldVer: Int, newVer: Int) {

  }
}

// break up into scope-specific traits that just need the db connection
// i.e. one that handles views or one that handles stories, something like that
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

  case class ClassifierComponent(
    table: String,
    extractor: Story => TraversableOnce[String])

  val components = List(
    ClassifierComponent("domain", (s:Story) => Classifier.domain(s.link)),
    ClassifierComponent("word", (s:Story) => Classifier.words(s.title)),
    ClassifierComponent("bigram", (s:Story) => Classifier.bigrams(s.title)),
    ClassifierComponent("trigram", (s:Story) => Classifier.trigrams(s.title)))

  def classify(story: Story): (Double, Double) = {
    val posDocs = prefs.getLong("positive_headline_count", 0)
    val negDocs = prefs.getLong("negative_headline_count", 0)
    val totDocs: Double = (posDocs + negDocs).toDouble max 1e-5

    var positive: Double = posDocs / totDocs
    var negative: Double = negDocs / totDocs

    components.foreach {
      (comp:ClassifierComponent) =>
      val total = db.query("select count(*) from %s".format(comp.table))
        .singleRow[Long](_.getLong(0))
      val posDenom = db.query(
        "select count(*) from %s where positive > 0".format(comp.table))
        .singleRow[Double](_.getLong(0) + total)
      val negDenom = db.query(
        "select count(*) from %s where negative > 0".format(comp.table))
        .singleRow[Double](_.getLong(0) + total)
      for (item <- comp.extractor(story)) {
        db.query(
          "select positive, negative from %s where repr = '%s'"
          .format(comp.table, item)).ifExists {
            (c: Cursor) =>
              positive *= ((c.getLong(0) + 1)/posDenom)
              negative *= ((c.getLong(1) + 1)/negDenom)
          }
      }
    }
    (positive, negative)
  }

  /** Adds stories with the given ids to the classifier, belonging to the
   *    class given by storyType
   */
  def trainClassifier(ids: Array[Long], storyType: StoryType) {
    val editor = prefs.edit()
    val collections = components.map((c) => (c, new HashMap[String, Int]() {
      override def default(key: String): Int = 0
    }))
    val idString = ids.mkString(", ")
    val (thisClass, otherClass) = storyType match {
      case PositiveStory => ("positive", "negative")
      case NegativeStory => ("negative", "positive")
    }

    db.query(
      "select title, link from story where _id in (%s)".format(idString))
    .foreach {
      (c: Cursor) =>
        val story = Story(c.getString(0), c.getString(1))
        collections.foreach {
          (comp: (ClassifierComponent, HashMap[String, Int])) =>
            for (item <- comp._1.extractor(story)) {
              comp._2(item) = comp._2(item) + 1
            }
        }
    }

    // Another spot where a wrapped non-exclusive transaction would be helpful
    collections.foreach {
      (comp: (ClassifierComponent, HashMap[String, Int])) =>
        for (item <- comp._2.keySet) {
          val values = new ContentValues()
          db.query(
            "select _id, %s from %s where repr = '%s'"
            .format(thisClass, comp._1.table, item)).ifExists {
              (c: Cursor) =>
                values.put(thisClass, java.lang.Long.valueOf(
                  c.getLong(1) + comp._2(item)))
                db.update(comp._1.table, values, "_id = ?",
                          Array(c.getLong(0).toString))
            } otherwise {
              // The "put" method here truncates leading 0s on string
              //    representations of numbers, e.g. converting "000" to "0"
              values.put("repr", item)
              values.put(thisClass,
                         java.lang.Long.valueOf(comp._2(item)))
              values.put(otherClass, java.lang.Long.valueOf(0))
              db.insert(comp._1.table, null, values)
            }
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
