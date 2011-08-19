package com.example.nwsr

import android.app.ListActivity
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.preference.PreferenceActivity
import android.preference.PreferenceManager
import android.view.ContextMenu
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.SimpleCursorAdapter
import android.widget.TextView

import scala.collection.mutable.ArrayBuilder


class NWSR extends ListActivity {
  var db: NWSRDatabase = _
  var cursor: Cursor = _
  var adapter: SimpleCursorAdapter = _

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    setTitle(R.string.title_headlines)
    setContentView(R.layout.headlines)
    PreferenceManager.setDefaultValues(this, R.xml.settings, false)

    val inflater = LayoutInflater.from(this)
    val footer = inflater.inflate(R.layout.button_next_headline, null)
      .asInstanceOf[TextView]
    getListView.addFooterView(footer)
    footer.setOnClickListener(new View.OnClickListener() {
      def onClick(v: View) {
        val arr = ArrayBuilder.make[Long]
        cursor.moveToFirst()
        while (!cursor.isAfterLast) {
          arr += cursor.getLong(0)
          cursor.moveToNext()
        }
        db.filterStories(arr.result(), false)
        updateViews()
      }
    })

    db = new NWSRDatabase(this)
    cursor = db.stories()
    adapter = new SimpleCursorAdapter(
      this, R.layout.headline, cursor, Array("title", "link"),
      Array(R.id.headline_title,R.id.headline_link))
    setListAdapter(adapter)
    registerForContextMenu(getListView)
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

  override def onCreateOptionsMenu(menu: Menu): Boolean = {
    val inflater = getMenuInflater()
    inflater.inflate(R.menu.headlines, menu)
    true
  }

  override def onOptionsItemSelected(item: MenuItem): Boolean = 
    item.getItemId() match {
      case R.id.refresh => true
      case R.id.feeds => {
        startActivity(new Intent(this, classOf[NWSRFeeds]))
        true
      }
      case R.id.settings => {
        startActivity(new Intent(this, classOf[NWSRSettings]))
        true
      }
      case _ => super.onOptionsItemSelected(item)
    }

  override def onCreateContextMenu(menu: ContextMenu, v: View,
                                   menuInfo: ContextMenu.ContextMenuInfo) {
    super.onCreateContextMenu(menu, v, menuInfo)
    if (menuInfo.asInstanceOf[AdapterView.AdapterContextMenuInfo].id >= 0) {
      val inflater = getMenuInflater()
      inflater.inflate(R.menu.context_headlines, menu)
      menu.setHeaderTitle(
        menuInfo.asInstanceOf[AdapterView.AdapterContextMenuInfo].targetView
        .findViewById(R.id.headline_title).asInstanceOf[TextView].getText)
    }
  }

  override def onContextItemSelected(item: MenuItem): Boolean = {
    val info = item.getMenuInfo().asInstanceOf[
      AdapterView.AdapterContextMenuInfo]
    item.getItemId() match {
      case R.id.open_browser => {
        val url = info.targetView.findViewById(R.id.headline_link)
          .asInstanceOf[TextView].getText.toString
        db.filterStories(Array(info.id), true)
        // Temporarily disable opening in browser
        val intent = new Intent(Intent.ACTION_VIEW)
          .setData(Uri.parse(if (url.startsWith("http://")) url
                             else "http://" + url))
        //startActivity(intent)
        true
      }
      case _ => super.onContextItemSelected(item)
    }
  }
}


class NWSRSettings extends PreferenceActivity {
  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    addPreferencesFromResource(R.xml.settings);
  }
}
