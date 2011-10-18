package com.aquamentis.nwsr

import android.app.Dialog
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.ContextMenu
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ListView
import android.widget.SimpleCursorAdapter
import android.widget.TextView

import scala.collection.mutable.ArrayBuilder

import com.aquamentis.util.RichDatabase._
import com.aquamentis.util.Story

class Headlines extends DatabaseActivity with DialogFeedRetriever
with SharedPreferences.OnSharedPreferenceChangeListener {

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    setTitle(R.string.title_headlines)
    setContentView(R.layout.headlines)

    val inflater = LayoutInflater.from(this)
    getListView.addFooterView(
      inflater.inflate(R.layout.button_next_headline, null))

    registerForContextMenu(getListView)    
    cursor = db.storyView()
    adapter = new SimpleCursorAdapter(
      this, R.layout.headline, cursor, Array("title", "link"),
      Array(R.id.headline_title, R.id.headline_link))
    setListAdapter(adapter)

    PreferenceManager.setDefaultValues(this, R.xml.settings, false)
    PreferenceManager.getDefaultSharedPreferences(this)
      .registerOnSharedPreferenceChangeListener(this)
  }

  override def onResume() {
    super.onResume()
    updateView()
  }

  override def onCreateOptionsMenu(menu: Menu): Boolean = {
    val inflater = getMenuInflater()
    inflater.inflate(R.menu.headlines, menu)
    true
  }

  override def onOptionsItemSelected(item: MenuItem): Boolean = 
    item.getItemId match {
      case R.id.refresh => {
        new ForegroundRetrieveTask().execute(FeedAll)
        true
      }
      case R.id.feeds => {
        startActivity(new Intent(this, classOf[NewsFeeds]))
        true
      }
      case R.id.saved => {
        startActivity(new Intent(this, classOf[SavedItems]))
        true
      }
      case R.id.settings => {
        startActivity(new Intent(this, classOf[Settings]))
        true
      }
      case _ => super.onOptionsItemSelected(item)
    }

  override def onListItemClick(lv: ListView, v: View, pos: Int, id: Long) {
    if (id < 0) {
      val ids = {
        val arr = ArrayBuilder.make[Long]
        cursor.foreach {
          arr += cursor.getLong(0)
        }
        arr.result()
      }
      db.hideStories(ids)
      updateView()
      getListView.setSelectionAfterHeaderView()
      val intent = new Intent(this, classOf[TrainingService])
      intent.putExtra("ids", ids)
      intent.putExtra("class", 0)
      startService(intent)
    }
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
    val info = item.getMenuInfo.asInstanceOf[
      AdapterView.AdapterContextMenuInfo]
    item.getItemId match {
      case R.id.open_browser => {
        val url = info.targetView.findViewById(R.id.headline_link)
          .asInstanceOf[TextView].getText.toString
        db.train(Array(info.id), PositiveStory)
        openInBrowser(url)
        updateView()
        true
      }
      case R.id.save => {
        db.addSaved(Story(
          info.targetView.findViewById(R.id.headline_title)
            .asInstanceOf[TextView].getText.toString,
          info.targetView.findViewById(R.id.headline_link)
            .asInstanceOf[TextView].getText.toString))
        db.train(Array(info.id), PositiveStory)
        updateView()
        true
      }
      case R.id.delete => {
        db.train(Array(info.id), NegativeStory)
        updateView()
        false
      }
      case _ => super.onContextItemSelected(item)
    }
  }

  def onSharedPreferenceChanged(sp: SharedPreferences, key: String) {
    if (key == "stories_per_page") {
      cursor = db.storyView()
      adapter.changeCursor(cursor)
    }
  }
}
