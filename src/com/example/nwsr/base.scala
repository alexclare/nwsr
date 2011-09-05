package com.example.nwsr

import android.app.AlertDialog
import android.app.Dialog
import android.app.ProgressDialog
import android.app.ListActivity
import android.content.DialogInterface
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.widget.SimpleCursorAdapter

import java.io.FileNotFoundException
import java.io.IOException
import java.net.UnknownHostException

import org.xml.sax.SAXParseException

import com.example.util.Feed
import com.example.util.NotFeedException


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
                  .setData(Uri.parse(if (url.contains("://")) url
                                     else "http://" + url)))
  }
}


abstract class NewsActivity extends DatabaseActivity {
  activity =>

  val FeedNotFound: Int = 0
  val FeedInvalid: Int = 1

  override def onCreateDialog(id: Int): Dialog = id match {
    case (FeedNotFound | FeedInvalid) => {
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
    case _ => super.onCreateDialog(id)
  }

  /** Unfortunately, the Scala compiler doesn't generate type signatures for
   *    this class that are recognizable by the Android VM once the program is
   *    running, hence the "Object" and casting
   */
  class RetrieveFeedTask extends AsyncTask[Object, Unit, Unit] {
    var dialog: ProgressDialog = _
    var error: Int = 0

    override def onPreExecute() {
      dialog = ProgressDialog.show(activity, "", "Retrieving...", true)
    }

    def doInBackground(linkOrId: Object*) {
      linkOrId(0).asInstanceOf[Either[String, Option[Long]]] match {
        case Left(link) => addFeed(None, link, None, None, true)
        case Right(id) => {
          val cursor = db.feedsToRefresh(id)
          cursor.moveToFirst()
          while(!cursor.isAfterLast) {
            addFeed(
              Some(cursor.getLong(0)), cursor.getString(1),
              cursor.getString(2) match {
                case null => None
                case e => Some(e)
              },
              cursor.getString(3) match {
                case null => None
                case l => Some(l)
              }, false)
            cursor.moveToNext()
          }
          cursor.close()
        }
      }
    }

    override def onPostExecute(a:Unit) {
      updateView()
      dialog.dismiss()
    }

    override def onCancelled() {
      dialog.dismiss()
      showDialog(error)
    }

    def addFeed(id: Option[Long], link: String, etag: Option[String],
                lastMod: Option[String], cancelOnError: Boolean) {
      try {
        Feed.refresh(link, etag, lastMod) match {
          case Some(f) => db.addFeed(f, id)
          case None =>
        }
      } catch {
        case _ @ (_: FileNotFoundException | _: UnknownHostException |
                  _: IOException)
        => if (cancelOnError) {
          error = FeedNotFound
          cancel(false)
        }
        case _ @ (_: SAXParseException | _: NotFeedException)
        => if (cancelOnError) {
          error = FeedInvalid
          cancel(false)
        }
      }
    }
  }
}
