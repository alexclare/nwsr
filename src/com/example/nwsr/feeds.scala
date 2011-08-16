package com.example.nwsr

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.app.ListActivity
import android.content.DialogInterface
import android.content.Intent
import android.database.Cursor
import android.os.Bundle
import android.view.ContextMenu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.EditText
import android.widget.SimpleCursorAdapter

import java.net.URL
import java.net.HttpURLConnection
import java.net.UnknownHostException

import org.xml.sax.SAXParseException

class NWSRFeeds extends ListActivity {
  var db: NWSRDatabase = _
  var cursor: Cursor = _
  var adapter: SimpleCursorAdapter = _

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.feeds);
    
    val parent = this
    findViewById(R.id.feeds_add_button).setOnClickListener(
      new View.OnClickListener {
        def onClick(v: View) {
          startActivityForResult(new Intent(parent, classOf[NWSRAddFeed]), 0)
        }
      })

    db = new NWSRDatabase(this)
    cursor = db.feeds()
    adapter = new SimpleCursorAdapter(
      this, R.layout.feed, cursor, Array("title"), Array(R.id.feed_title))
    setListAdapter(adapter)

    registerForContextMenu(getListView())
  }

  override def onActivityResult(request: Int, result: Int, data: Intent) {
    result match {
      case Activity.RESULT_OK => {
        val base = data.getStringExtra("url")
        val url = new URL(if (base.startsWith("http://")) base
                          // also https
                          else "http://" + base)
        val connection = url.openConnection().asInstanceOf[HttpURLConnection]
        try {
          val istream = connection.getInputStream()
          val data = FeedData.parseFeed(istream)
          // parseFeed might take a long time; feedback here?
          // heck, this whole process (parsing, bayes filter, removing dupes) might
          val id = db.addFeed(data.title, data.link)
          for (story <- data.stories)
            db.addStory(story.title, story.link, id)
          cursor.requery()
          adapter.notifyDataSetChanged()
        } catch {
          case _ : UnknownHostException => showDialog(0)
          case _ : SAXParseException => showDialog(1)
          case _ : NotFeedException => showDialog(1)
        } finally {
          connection.disconnect()
        }
      }
      case _ =>
    }
  }

  override def onCreateDialog(id: Int): Dialog = {
    val builder = new AlertDialog.Builder(this)
    builder.setCancelable(false)
    builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
      def onClick(dialog: DialogInterface, id: Int) {
        dialog.dismiss()
      }
    })
    builder.setTitle("Add feed error")
    builder.setMessage(id match {
      case 0 => "Could not load URL"
      case 1 => "Not a valid RSS/Atom feed"
      case _ => "Unknown Error"
    })
    builder.create()
  }

  override def onCreateContextMenu(menu: ContextMenu, v: View,
                                   menuInfo: ContextMenu.ContextMenuInfo) {
    super.onCreateContextMenu(menu, v, menuInfo)
    val inflater = getMenuInflater()
    inflater.inflate(R.menu.context_feeds, menu)
  }

  override def onContextItemSelected(item: MenuItem): Boolean = {
    val info = item.getMenuInfo().asInstanceOf[
      AdapterView.AdapterContextMenuInfo]
    item.getItemId() match {
      case R.id.delete => {
        db.deleteFeed(info.id)
        cursor.requery()
        adapter.notifyDataSetChanged()
        true
      }
      case _ => super.onContextItemSelected(item)
    }
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
