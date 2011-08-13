package com.example.nwsr

import android.app.Activity
import android.app.ListActivity
import android.content.Intent
import android.database.Cursor
import android.os.Bundle
import android.view.ContextMenu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.EditText
import android.widget.SimpleCursorAdapter

class NWSRFeeds extends ListActivity {
  var db: NWSRDatabase = _
  var cursor: Cursor = _
  var adapter: SimpleCursorAdapter = _

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.feeds);

    db = new NWSRDatabase(this)
    
    val parent = this
    findViewById(R.id.feeds_add_button).setOnClickListener(
      new View.OnClickListener {
        def onClick(v: View) {
          startActivityForResult(new Intent(parent, classOf[NWSRAddFeed]), 0)
        }
      })

    cursor = db.feeds()
    adapter = new SimpleCursorAdapter(
      this, R.layout.feed, cursor, Array("title"), Array(R.id.feed_title))
    setListAdapter(adapter)

    registerForContextMenu(getListView())
  }

  override def onActivityResult(request: Int, result: Int, data: Intent) {
    result match {
      case Activity.RESULT_OK => {
        db.addFeed(data.getStringExtra("url"))
        cursor.requery()
        adapter.notifyDataSetChanged()
      }
      case _ =>
    }
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
