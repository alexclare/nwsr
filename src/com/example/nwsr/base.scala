package com.example.nwsr

import android.app.AlertDialog
import android.app.Dialog
import android.app.ListActivity
import android.content.DialogInterface
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.widget.SimpleCursorAdapter

import java.io.FileNotFoundException
import java.net.UnknownHostException

import org.xml.sax.SAXParseException

abstract class DatabaseActivity extends ListActivity {
  var db: NWSRDatabase = _
  var cursor: Cursor = _
  var adapter: SimpleCursorAdapter = _

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    db = new NWSRDatabase(this).open()
  }

  override def onDestroy() {
    super.onDestroy()
    cursor.close()
    db.close()
  }

  def updateView() {
    cursor.requery()
    adapter.notifyDataSetChanged()
  }

  def openInBrowser(url: String) {
    startActivity(new Intent(Intent.ACTION_VIEW)
                  .setData(Uri.parse(if (url.startsWith("http://")) url
                                     else "http://" + url)))
  }
}


abstract class NewsActivity extends DatabaseActivity {
  val FeedNotFound: Int = 0
  val FeedInvalid: Int = 1

  val errorDialogs: Boolean

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

  def refreshFeed(link: String) {
    refreshFeed(None, link, None, None)
  }

  def refreshFeed(id: Option[Long], link: String, etag: Option[String],
                  lastModified: Option[String]) {
    try {
      Feed.refresh(link, etag, lastModified) match {
        case Some(feed) => {
          val newId = db.addFeed(feed, id)
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
