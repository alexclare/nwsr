package com.example.nwsr

import android.app.AlertDialog
import android.app.Dialog
import android.app.ListActivity
import android.content.DialogInterface
import android.database.Cursor
import android.os.Bundle
import android.widget.SimpleCursorAdapter

import java.io.FileNotFoundException
import java.net.UnknownHostException

import org.xml.sax.SAXParseException

abstract class NewsActivity extends ListActivity {
  val FeedNotFound: Int = 0
  val FeedInvalid: Int = 1

  val errorDialogs: Boolean

  var db: NWSRDatabase = _
  var cursor: Cursor = _
  var adapter: SimpleCursorAdapter = _

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    db = new NWSRDatabase(this).open()
  }

  override def onResume() {
    super.onResume()
    updateView()
  }

  override def onDestroy() {
    super.onDestroy()
    cursor.close()
    db.close()
  }

  override def onCreateDialog(id: Int): Dialog = {
    val builder = new AlertDialog.Builder(this)
    builder.setCancelable(false)
    builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
      def onClick(dialog: DialogInterface, id: Int) {
        dialog.dismiss()
      }
    })
    builder.setTitle("Retrieve feed error")
    builder.setMessage(id match {
      case FeedNotFound => "Could not load URL"
      case FeedInvalid => "Not a valid RSS/Atom feed"
      case _ => "Unknown Error"
    })
    builder.create()
  }

  def updateView() {
    cursor.requery()
    adapter.notifyDataSetChanged()
  }

  def addFeed(base: String) {
    try {
      Feed.retrieve(base) match {
      // parseFeed might take a long time; feedback here?
      // heck, this whole process (parsing, bayes filter, removing dupes) might
        case Some(feed) => {
          val id = db.addFeed(feed, None)
          for (story <- feed.stories) {
            db.addStory(story, id)
          }
        }
        case None =>
      }
    } catch {
      case _ : FileNotFoundException =>
        if (errorDialogs) showDialog(FeedNotFound)
      case _ : UnknownHostException =>
        if (errorDialogs) showDialog(FeedNotFound)
      case _ : SAXParseException => if (errorDialogs) showDialog(FeedInvalid)
      case _ : NotFeedException => if (errorDialogs) showDialog(FeedInvalid)
    }
  }

  def refreshFeed (id: Long, link: String, etag: Option[String],
                   lastModified: Option[String]) {
    try {
      Feed.refresh(link, etag, lastModified) match {
        case Some(feed) => {
          val newId = db.addFeed(feed, Some(id))
          for (story <- feed.stories) {
            db.addStory(story, newId)
          }
        }
        case None =>
      }
    } catch {
      case _ : FileNotFoundException =>
        if (errorDialogs) showDialog(FeedNotFound)
      case _ : UnknownHostException =>
        if (errorDialogs) showDialog(FeedNotFound)
      case _ : SAXParseException => if (errorDialogs) showDialog(FeedInvalid)
      case _ : NotFeedException => if (errorDialogs) showDialog(FeedInvalid)
    }
  }
}
