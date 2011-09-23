package com.aquamentis.nwsr

import android.app.IntentService
import android.content.Intent

object Classifier {
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

  def normalize(str: String) = punctuation.replaceAllIn(str, " ")
    .toLowerCase().split(' ')
    .filter((word) => word.length > 2 && !commonWords.contains(word))
}

sealed abstract class StoryType
case object PositiveStory extends StoryType
case object NegativeStory extends StoryType


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
