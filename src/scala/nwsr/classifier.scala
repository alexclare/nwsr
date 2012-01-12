package com.aquamentis.nwsr

import android.app.IntentService
import android.content.ContentValues
import android.content.Intent
import android.content.SharedPreferences
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase

import scala.collection.mutable.HashMap
import scala.math.{exp, log}

import com.aquamentis.util.Story
import com.aquamentis.util.RichDatabase._

sealed abstract class StoryType
case object PositiveStory extends StoryType
case object NegativeStory extends StoryType

/**
 * Certainly a US-English-centric portion of the program, and one that could
 * probably be adapted without much loss in quality; bigrams
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

  /** Common stop words from the Python NLTK (www.nltk.org), which are in turn from
   *  http://anoncvs.postgresql.org/cvsweb.cgi/pgsql/src/backend/snowball/stopwords/
   */
  val commonWords = Set(
    "myself","our","ours","ourselves","you","your","yours","yourself",
    "yourselves","him","his","himself","she","her","hers","herself","its",
    "itself","they","them","their","theirs","themselves","what","which","who",
    "whom","this","that","these","those","are","was","were","been","being",
    "have","has","had","having","does","did","doing","the","and","but",
    "because","until","while","for","with","about","against","between","into",
    "through","during","before","after","above","below","from","down","out",
    "off","over","under","again","further","then","once","here","there","when",
    "where","why","how","all","any","both","each","few","more","most","other",
    "some","such","nor","not","only","own","same","than","too","very","can",
    "will","just","should","now")

  def words(str: String) = punctuation.replaceAllIn(str, " ")
    .toLowerCase().split(' ')
    .filter((word) => word.length > 2 && !commonWords.contains(word))

  def ngram(str: String, n: Int) = punctuation.split(str)
    .flatMap(_.toLowerCase.split(' ').filter(_.length > 0)
             .sliding(n).filterNot(_.size < n)
             .map(_.reduceLeft(_ + " " + _)).toTraversable)

  def bigrams(str: String) = ngram(str, 2)
}


trait Classifier {
  def db: SQLiteDatabase
  def prefs: SharedPreferences

  case class Feature(
    table: String,
    extract: Story => TraversableOnce[String])

  val features = List(
    Feature("domain", (s:Story) => Classifier.domain(s.link)),
    Feature("word", (s:Story) => Classifier.words(s.title)),
    Feature("bigram", (s:Story) => Classifier.bigrams(s.title)))

  def classify(story: Story): Double = {
    val posDocs = prefs.getLong("positive_headline_count", 0)
    val negDocs = prefs.getLong("negative_headline_count", 0)
    val totDocs = log(posDocs + negDocs) max 0.0

    var positive: Double = log(posDocs) - totDocs
    var negative: Double = log(negDocs) - totDocs

    features.foreach {
      (feature:Feature) =>
      val posDenom = log(db.query(
        "select sum(positive+1) from %s".format(feature.table))
        .singleRow[Double](_.getLong(0)))
      val negDenom = log(db.query(
        "select sum(negative+1) from %s".format(feature.table))
        .singleRow[Double](_.getLong(0)))
      for (item <- feature.extract(story)) {
        db.query(
          "select positive, negative from %s where repr = '%s'"
          .format(feature.table, item)).ifExists {
            (c: Cursor) =>
              positive += log(c.getLong(0) + 1) - posDenom
              negative += log(c.getLong(1) + 1) - negDenom
          }
      }
    }
    positive - negative
  }

  def train(ids: Array[Long], storyType: StoryType) {
    val editor = prefs.edit()
    val tally = features.map {
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
        tally.foreach {
          (feature: (Feature, HashMap[String, Int])) =>
            for (item <- feature._1.extract(story)) {
              feature._2(item) = feature._2(item) + 1
            }
        }
    }

    db.exclusiveTransaction {
      tally.foreach {
        (feature: (Feature, HashMap[String, Int])) =>
          for (item <- feature._2.keySet) {
            db.execSQL("insert or ignore into %s values ('%s', 0, 0)".format(
              feature._1.table, item))
            db.execSQL("update %s set %s = %s + %s where repr = '%s'".format(
              feature._1.table, thisClass, thisClass, feature._2(item), item))
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
    db = NWSRDatabase(this)
  }

  override def onHandleIntent(intent: Intent) {
    val ids = intent.getLongArrayExtra("ids").asInstanceOf[Array[Long]]
    val cls = intent.getIntExtra("class", 0)
    // Process at most 5 stories at a time to minimize the lock on the database
    //   (more of a stopgap measure; we'll see if this has much of an effect)
    for (arr <- ids.sliding(5, 5)) {
      db.train(arr, if (cls == 0) NegativeStory else PositiveStory)
    }
  }
}
