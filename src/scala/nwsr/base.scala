package com.aquamentis.nwsr

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

import com.aquamentis.util.Feed
import com.aquamentis.util.NotFeedException
import com.aquamentis.util.RichDatabase._

object DialogType extends Enumeration {
  type DialogType = Value
  val FeedNotFound, FeedInvalid, AddFeed, DeleteAllSaved = Value
  implicit def toInt(dlg: DialogType): Int = dlg.id
}
import DialogType._


abstract class DatabaseActivity extends ListActivity {
  var db: NWSRDatabase = _
  var cursor: Cursor = _
  var adapter: SimpleCursorAdapter = _

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    db = NWSRDatabase(this)
  }

  override def onDestroy() {
    super.onDestroy()
    cursor.close()
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


sealed abstract class FeedRequest
case class FeedLink(link: String) extends FeedRequest
case class FeedId(id: Long) extends FeedRequest
case object FeedAll extends FeedRequest

trait FeedRetriever {
  def db: NWSRDatabase

  def retrieveFeed(req: FeedRequest) {
    req match {
      case FeedAll => db.purgeOld()
      case _ =>
    }
    req match {
      case FeedLink(link: String) => addFeed(None, link, None, None)
      case (_: FeedId | FeedAll) => {
        val cursor = db.feedsToRefresh(req)
        cursor.foreach {
          addFeed(Some(cursor.getLong(0)), cursor.getString(1),
                  cursor.getString(2) match {
                    case null => None
                    case e => Some(e)
                  },
                  cursor.getString(3) match {
                    case null => None
                    case l => Some(l)
                  })
        }
        cursor.close()
      }
    }
  }

  def addFeed(id: Option[Long], link: String, etag: Option[String],
              lastMod: Option[String]) {
    try {
      Feed.refresh(link, etag, lastMod) match {
        case Some(f) => db.addFeed(f, id)
        case None =>
      }
    } catch {
      case (_: FileNotFoundException | _: UnknownHostException |
            _: IOException) => handleFeedNotFound()
      case (_: SAXParseException | _: NotFeedException) => handleFeedInvalid()
    }
  }

  def handleFeedNotFound()
  def handleFeedInvalid()
}

trait SilentFeedRetriever extends FeedRetriever {
  def handleFeedNotFound() = { }
  def handleFeedInvalid() = { }
}

trait DialogFeedRetriever extends DatabaseActivity {
  activity =>

  override def onCreateDialog(id: Int): Dialog = {
    val dialog = DialogType(id)
    dialog match {
      case (FeedNotFound | FeedInvalid) => {
        val builder = new AlertDialog.Builder(this)
        builder.setCancelable(false)
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
          def onClick(dialog: DialogInterface, id: Int) {
            dialog.dismiss()
          }
        })
        builder.setTitle("Retrieve feed error")
        builder.setMessage(dialog match {
          case FeedNotFound => "Could not load URL"
          case FeedInvalid => "Not a valid RSS/Atom feed"
          case _ => "Unknown Error"
        })
        builder.create()
      }
      case _ => super.onCreateDialog(id)
    }
  }

  /** Unfortunately, the Scala compiler doesn't generate type signatures for
   *    this class that are recognizable by the Android VM once the program is
   *    running, hence the "Object" and casting
   */
  class ForegroundRetrieveTask extends AsyncTask[Object, Unit, Unit]
  with SilentFeedRetriever {
    def db = activity.db
    var dialog: ProgressDialog = _

    override def onPreExecute() {
      dialog = ProgressDialog.show(activity, "", "Retrieving...", true)
    }

    def doInBackground(request: Object*) {
      retrieveFeed(request(0).asInstanceOf[FeedRequest])
    }

    override def onPostExecute(a:Unit) {
      updateView()
      dialog.dismiss()
    }

    override def onCancelled() {
      dialog.dismiss()
    }
  }

  class DialogRetrieveTask extends ForegroundRetrieveTask {
    var error: DialogType = _

    override def onCancelled() {
      dialog.dismiss()
      showDialog(error)
    }

    override def handleFeedNotFound() {
      error = FeedNotFound
      cancel(false)
    }

    override def handleFeedInvalid() {
      error = FeedInvalid
      cancel(false)
    }
  }
}

