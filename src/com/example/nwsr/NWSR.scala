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


class NWSR extends ListActivity {
  var db: NWSRDatabase = _
  var cursor: Cursor = _
  var adapter: SimpleCursorAdapter = _
  var footer: TextView = _

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    setTitle(R.string.title_headlines)
    setContentView(R.layout.headlines)
    PreferenceManager.setDefaultValues(this, R.xml.settings, false)

    val inflater = LayoutInflater.from(this)
    footer = inflater.inflate(R.layout.button_next_headline, null)
      .asInstanceOf[TextView]
    getListView.addFooterView(footer)
    footer.setOnClickListener(new View.OnClickListener() {
      def onClick(v: View) {
        cursor.moveToFirst()
        while (!cursor.isAfterLast) {
          db.filterStory(cursor.getLong(0), false)
          cursor.moveToNext()
        }
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
        db.filterStory(info.id, true)
        val intent = new Intent(Intent.ACTION_VIEW)
          .setData(Uri.parse(if (url.startsWith("http://")) url
                             else "http://" + url))
        startActivity(intent)
        true
      }
      case _ => super.onContextItemSelected(item)
    }
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
}


class NWSRSettings extends PreferenceActivity {
  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    addPreferencesFromResource(R.xml.settings);
  }
}
