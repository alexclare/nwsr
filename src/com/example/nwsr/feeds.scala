package com.example.nwsr

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.ContextMenu
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.SimpleCursorAdapter
import android.widget.EditText
import android.widget.TextView

class NWSRFeeds extends NewsActivity {
  val errorDialogs = true


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

    cursor = db.feedView()
    adapter = new SimpleCursorAdapter(
      this, R.layout.feed, cursor, Array("title", "display_link"),
      Array(R.id.feed_title, R.id.feed_link))
    setListAdapter(adapter)

    if (getIntent.getAction == Intent.ACTION_VIEW) {
      // Issue 950 causes some feeds not to be recognized by the intent
      //   filter; fixed in 2.2
      addFeed(getIntent.getDataString)
      updateView()
    }
  }

  override def onActivityResult(request: Int, result: Int, data: Intent) {
    result match {
      case Activity.RESULT_OK => {
        addFeed(data.getStringExtra("url"))
        updateView()
      }
      case _ =>
    }
  }

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
      case R.id.refresh => {
        db.purgeOld()
        val link = db.refreshLink(info.id)
        refreshFeed(info.id, link._1, link._2, link._3)
        updateView()
        true
      }
      case R.id.delete => {
        db.deleteFeed(info.id)
        updateView()
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
