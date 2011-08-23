package com.example.nwsr

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.app.ListActivity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.database.Cursor
import android.os.Bundle
import android.view.ContextMenu
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.SimpleCursorAdapter
import android.widget.EditText
import android.widget.TextView

import java.net.UnknownHostException

import org.xml.sax.SAXParseException

// TODO: Replace arbitrary constants with enums/ints
class NWSRFeeds extends ListActivity with FeedErrorDialog {
  var db: NWSRDatabase = _
  var cursor: Cursor = _
  var adapter: SimpleCursorAdapter = _

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.feeds)

    val inflater = LayoutInflater.from(this)
    val header = inflater.inflate(R.layout.button_add_feed, null)
      .asInstanceOf[TextView]
    getListView.addHeaderView(header)

    val activity = this
    val ocl = new View.OnClickListener() {
      def onClick(v: View) {
        startActivityForResult(new Intent(activity, classOf[NWSRAddFeed]), 0)
      }
    }
    header.setOnClickListener(ocl)
    findViewById(android.R.id.empty).setOnClickListener(ocl)

    registerForContextMenu(getListView)

    db = new NWSRDatabase(this).open()
    cursor = db.feedView()
    adapter = new SimpleCursorAdapter(
      this, R.layout.feed, cursor, Array("title", "display_link"),
      Array(R.id.feed_title, R.id.feed_link))
    setListAdapter(adapter)

    if (getIntent.getAction == Intent.ACTION_VIEW) {
      // Issue 950 causes some feeds not to be recognized by the intent
      //   filter; fixed in 2.2
      addFeed(getIntent.getDataString)
    }
  }

  override def onResume() {
    super.onResume()
    updateViews()
  }

  def updateViews() {
    cursor.requery()
    adapter.notifyDataSetChanged()
  }

  override def onDestroy() {
    super.onDestroy()
    cursor.close()
    db.close()
  }

  override def onActivityResult(request: Int, result: Int, data: Intent) {
    result match {
      case Activity.RESULT_OK => {
        addFeed(data.getStringExtra("url"))
      }
      case _ =>
    }
  }

  override def onCreateDialog(id: Int): Dialog = createDialog(this, id)

  override def onCreateContextMenu(menu: ContextMenu, v: View,
                                   menuInfo: ContextMenu.ContextMenuInfo) {
    super.onCreateContextMenu(menu, v, menuInfo)
    if (menuInfo.asInstanceOf[AdapterView.AdapterContextMenuInfo].id >= 0) {
      val inflater = getMenuInflater()
      inflater.inflate(R.menu.context_feeds, menu)
      menu.setHeaderTitle(
        menuInfo.asInstanceOf[AdapterView.AdapterContextMenuInfo].targetView
        .findViewById(R.id.feed_title).asInstanceOf[TextView].getText)
    }
  }

  override def onContextItemSelected(item: MenuItem): Boolean = {
    val info = item.getMenuInfo().asInstanceOf[
      AdapterView.AdapterContextMenuInfo]
    item.getItemId() match {
      case R.id.delete => {
        db.deleteFeed(info.id)
        updateViews()
        true
      }
      case _ => super.onContextItemSelected(item)
    }
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
      case _ : UnknownHostException => showDialog(FeedNotFound)
      case _ : SAXParseException => showDialog(FeedInvalid)
      case _ : NotFeedException => showDialog(FeedInvalid)
    }
    updateViews()
  }
}

class NWSRAddFeed extends Activity {
  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.add_feed);

    findViewById(R.id.add_feed_ok).setOnClickListener(
      new View.OnClickListener {
        def onClick(v: View) {
          val intent = new Intent()
          val et = findViewById(R.id.add_feed_url).asInstanceOf[EditText]
          intent.putExtra("url", et.getText().toString())
          setResult(Activity.RESULT_OK, intent)
          finish()
        }
      })

    findViewById(R.id.add_feed_cancel).setOnClickListener(
      new View.OnClickListener {
        def onClick(v: View) {
          setResult(Activity.RESULT_CANCELED)
          finish()
        }
      })
  }
}

trait FeedErrorDialog {
  val FeedNotFound: Int = 0
  val FeedInvalid: Int = 1

  def createDialog(context: Context, id: Int): Dialog = {
    val builder = new AlertDialog.Builder(context)
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
}
