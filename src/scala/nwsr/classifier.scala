package com.aquamentis.nwsr

import android.app.IntentService
import android.content.Intent

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

sealed abstract class StoryType
case object PositiveStory extends StoryType
case object NegativeStory extends StoryType

// Modify for positive or negative
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
    db.trainClassifier(ids, NegativeStory)
  }
}
