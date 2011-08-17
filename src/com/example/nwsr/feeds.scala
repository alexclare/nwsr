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
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.TextView

import java.net.URL
import java.net.HttpURLConnection
import java.net.UnknownHostException

import org.xml.sax.SAXParseException

// TODO: Replace arbitrary constants with enums/ints
class NWSRFeeds extends ListActivity {
  var db: NWSRDatabase = _
  var cursor: Cursor = _
  var adapter: FeedListAdapter = _

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.feeds);

    db = new NWSRDatabase(this)
    cursor = db.feeds()
    adapter = new FeedListAdapter(this, cursor)
    setListAdapter(adapter)

    val activity = this
    getListView.setOnItemClickListener(
      new AdapterView.OnItemClickListener() {
        def onItemClick(parent: AdapterView[_], view: View,
                        position: Int, id: Long) {
          if (position == 0)
            startActivityForResult(
              new Intent(activity, classOf[NWSRAddFeed]), 0)
        }
      })
    registerForContextMenu(getListView)
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
    // Not a good solution; still highlights the "add feed" item with long press
    super.onCreateContextMenu(menu, v, menuInfo)
    if (menuInfo.asInstanceOf[AdapterView.AdapterContextMenuInfo].id >= 0) {
      val inflater = getMenuInflater()
      inflater.inflate(R.menu.context_feeds, menu)
    }
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


class FeedListAdapter (context: Context, cursor: Cursor) extends BaseAdapter {
  val inflater = LayoutInflater.from(context)

  override def getCount(): Int = cursor.getCount + 1

  override def getItem(position: Int): Object = position match {
    case 0 => null
    case p => {
      cursor.moveToPosition(p-1)
      cursor.getString(1)
    }
  }

  override def getItemId(position: Int): Long = position match {
    case 0 => -1
    case p => {
      cursor.moveToPosition(p-1)
      cursor.getLong(0)
    }
  }

  override def getView(position: Int, convertView: View,
                       parent: ViewGroup): View = position match {
    case 0 => inflater.inflate(R.layout.button_add_feed, null)
    case p => if (convertView == null || convertView.getTag == null) {
      val view = inflater.inflate(R.layout.feed, null)
      val tv = view.findViewById(R.id.feed_title).asInstanceOf[TextView]
      tv.setText(getItem(position).asInstanceOf[String])
      view.setTag(tv)
      view
    } else {
      convertView.getTag.asInstanceOf[TextView]
        .setText(getItem(position).asInstanceOf[String])
      convertView
    }
  }

}
