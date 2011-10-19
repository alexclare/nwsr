package com.aquamentis.nwsr

import android.app.IntentService
import android.content.ContentValues
import android.content.Intent
import android.content.SharedPreferences
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase

import scala.collection.mutable.HashMap

import com.aquamentis.util.Story
import com.aquamentis.util.RichDatabase._

sealed abstract class StoryType
case object PositiveStory extends StoryType
case object NegativeStory extends StoryType

/**
 * Certainly a US-English-centric portion of the program, and one that could
 * probably be adapted without much loss in quality; bigrams and trigrams
 * aren't even filtered due to a lack of data regarding common ones.
 *
 * Common words don't have much effect on the filter, and take up little space
 * in the grand scheme of things.
 */
object Classifier {
  // Exclude both ASCII and Unicode punctuation
  val punctuation = "[\\p{Punct}&&\\p{P}]+".r

  def domain(str: String): Array[String] = {
    val regex = "(?:([^:]*)://)?(?:([^/]*)/?)+?".r
    Array(regex.findFirstMatchIn(str) match {
      case None => ""
      case Some(m) => if (m.groupCount >= 2) m.group(2) else ""
    })
  }

  val commonWords = Set(
    "the","and","that","have","for","not","with","you","this",
    "but","his","from","they","say","her","she","will","one","all","would",
    "there","their","what","out","about","who","get","which","when","make",
    "can","like","time","just","him","know","take","person","into","year",
    "your","good","some","could","them","see","other","than","then","now",
    "look","only","come","its","over","think","also","back","after","use",
    "two","how","our","work","first","well","way","even","new","want",
    "because","any","these","give","day","most")

  def words(str: String) = punctuation.replaceAllIn(str, " ")
    .toLowerCase().split(' ')
    .filter((word) => word.length > 2 && !commonWords.contains(word))

  def ngram(str: String, n: Int) = punctuation.split(str)
    .flatMap(_.toLowerCase.split(' ').filter(_.length > 0)
             .sliding(n).filterNot(_.size < n)
             .map(_.reduceLeft(_ + " " + _)).toTraversable)

  def bigrams(str: String) = ngram(str, 2)
  def trigrams(str: String) = ngram(str, 3)
}


trait Classifier {
  def db: SQLiteDatabase
  def prefs: SharedPreferences

  case class Characteristic(
    table: String,
    extractor: Story => TraversableOnce[String])

  val characteristics = List(
    Characteristic("domain", (s:Story) => Classifier.domain(s.link)),
    Characteristic("word", (s:Story) => Classifier.words(s.title)),
    Characteristic("bigram", (s:Story) => Classifier.bigrams(s.title)),
    Characteristic("trigram", (s:Story) => Classifier.trigrams(s.title)))

  def classify(story: Story): (Double, Double) = {
    val posDocs = prefs.getLong("positive_headline_count", 0)
    val negDocs = prefs.getLong("negative_headline_count", 0)
    val totDocs: Double = (posDocs + negDocs).toDouble max 1e-5

    var positive: Double = posDocs / totDocs
    var negative: Double = negDocs / totDocs

    characteristics.foreach {
      (char:Characteristic) =>
      val total = db.query("select count(*) from %s".format(char.table))
        .singleRow[Long](_.getLong(0))
      val posDenom = db.query(
        "select count(*) from %s where positive > 0".format(char.table))
        .singleRow[Double](_.getLong(0) + total)
      val negDenom = db.query(
        "select count(*) from %s where negative > 0".format(char.table))
        .singleRow[Double](_.getLong(0) + total)
      for (item <- char.extractor(story)) {
        db.query(
          "select positive, negative from %s where repr = '%s'"
          .format(char.table, item)).ifExists {
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
  def train(ids: Array[Long], storyType: StoryType) {
    val editor = prefs.edit()
    val collections = characteristics.map {
      (c) => (c, new HashMap[String, Int]() {
      override def default(key: String): Int = 0
      })}
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
          (char: (Characteristic, HashMap[String, Int])) =>
            for (item <- char._1.extractor(story)) {
              char._2(item) = char._2(item) + 1
            }
        }
    }

    db.exclusiveTransaction {
      collections.foreach {
        (char: (Characteristic, HashMap[String, Int])) =>
          for (item <- char._2.keySet) {
            db.execSQL("insert or ignore into %s values ('%s', 0, 0)".format(
              char._1.table, item))
            db.execSQL("update %s set %s = %s + %s where repr = '%s'".format(
              char._1.table, thisClass, thisClass, char._2(item), item))
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
}


class TrainingService extends IntentService ("NWSRTrainingService") {
  var db: NWSRDatabase = _

  override def onCreate() {
    super.onCreate()
    db = new NWSRDatabase(this).open()
  }

  override def onDestroy() {
    super.onDestroy()
    db.close()
  }

  override def onHandleIntent(intent: Intent) {
    val ids = intent.getLongArrayExtra("ids").asInstanceOf[Array[Long]]
    val cls = intent.getIntExtra("class", 0)
    db.train(ids, if (cls == 0) NegativeStory else PositiveStory)
  }
}
