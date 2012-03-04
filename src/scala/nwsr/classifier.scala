package com.aquamentis.nwsr

import android.app.IntentService
import android.content.ContentValues
import android.content.Intent
import android.content.SharedPreferences
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase

import scala.math.{exp, log}

import com.aquamentis.util.Story
import com.aquamentis.util.RichDatabase._

sealed abstract class StoryType
case object PositiveStory extends StoryType
case object NegativeStory extends StoryType


/** Extract and store features with varying method and shape
 *
 *  e.g. domain names need a fancy extract method, bigrams need a more complex
 *       SQL insert and select statement
 */
trait Feature {
  type T
  def table: String
  def extract(s: Story): Array[T]
  def where(value: T): String
  def empty(value: T): String

  def increment(value: T, cls: String, count: Int): String =
    "update %s set %s = %s + %s where %s"
    .format(table, cls, cls, count, where(value))

  def insert(value: T): String =
    "insert or ignore into %s values %s".format(table, empty(value))

  def select(value: T): String =
    "select positive, negative from %s where %s".format(table, where(value))
}

trait SingleStringFeature extends Feature {
  type T = String;
  def where(value: String) = "repr = '%s'".format(value)
  def empty(value: String) = "('%s', 0, 0)".format(value)
}

class FeatureTally (val feature: Feature) {
  import scala.collection.mutable.HashMap

  val tally = (new HashMap[feature.T, Int]() {
    override def default(key: feature.T): Int = 0
  })
}

object Feature {
  // Exclude both ASCII and Unicode punctuation
  val punctuation = "[\\p{Punct}&&\\p{P}]+".r

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

  val domain = new SingleStringFeature {
    val table = "domain"

    def extract(story: Story) = {
      val regex = "(?:([^:]*)://)?(?:([^/]*)/?)+?".r
      Array(regex.findFirstMatchIn(story.link) match {
        case None => ""
        case Some(m) => if (m.groupCount >= 2) m.group(2) else ""
      })
    }
  }

  val words = new SingleStringFeature {
    val table = "word"

    def extract(story: Story) =
      punctuation.replaceAllIn(story.title, " ")
        .toLowerCase().split(' ')
        .filter((word) => word.length > 2 && !commonWords.contains(word))
  }

  val bigrams = new Feature {
    type T = (String, String)
    val table = "bigram"

    def extract(story: Story) =
      punctuation.split(story.title)
        .map((x) => x.toLowerCase.split(' ')).filterNot(_.isEmpty)
        .flatMap((x) => x.zip(x.tail))
        .filter((x) => x._1.length > 2 && x._2.length > 2 &&
                !commonWords.contains(x._1) && !commonWords.contains(x._2))

    def where(value: (String, String)) =
      "repr1 = '%s' and repr2 = '%s'".format(value._1, value._2)

    def empty(value: (String, String)) =
      "('%s', '%s', 0, 0)".format(value._1, value._2)
  }

  val features = List(domain, words, bigrams)
}


trait Classifier {
  def db: SQLiteDatabase
  def prefs: SharedPreferences

  def classify(story: Story): Double = {
    val posDocs = prefs.getLong("positive_headline_count", 0)
    val negDocs = prefs.getLong("negative_headline_count", 0)
    val totDocs = log(posDocs + negDocs) max 0.0

    var positive: Double = log(posDocs) - totDocs
    var negative: Double = log(negDocs) - totDocs

    Feature.features.foreach {
      (feature:Feature) =>
      val posDenom = log(db.singleLongQuery(
        "select sum(positive+1) from %s".format(feature.table)))
      val negDenom = log(db.singleLongQuery(
        "select sum(negative+1) from %s".format(feature.table)))
      for (item <- feature.extract(story)) {
        db.query(feature.select(item)).ifExists {
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
    val feats = Feature.features.map((f) => new FeatureTally(f))
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
        feats.foreach {
          (ft: FeatureTally) =>
            for (item <- ft.feature.extract(story)) {
              ft.tally(item) = ft.tally(item) + 1
            }
        }
    }

    db.exclusiveTransaction {
      feats.foreach {
        (ft: FeatureTally) =>
          for (item <- ft.tally.keySet) {
            db.execSQL(ft.feature.insert(item))
            db.execSQL(ft.feature.increment(item, thisClass, ft.tally(item)))
          }
      }
    }

    val headlineKey = storyType match {
      case PositiveStory => "positive_headline_count"
      case NegativeStory => "negative_headline_count"
    }
    editor.putLong(headlineKey, prefs.getLong(headlineKey, 0) + ids.length)
    editor.commit()
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
