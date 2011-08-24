package com.example.nwsr

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
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


class NWSR extends NewsActivity
with SharedPreferences.OnSharedPreferenceChangeListener {
  val errorDialogs = false

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    setTitle(R.string.title_headlines)
    setContentView(R.layout.headlines)

    val inflater = LayoutInflater.from(this)
    val footer = inflater.inflate(R.layout.button_next_headline, null)
      .asInstanceOf[TextView]
    getListView.addFooterView(footer)
    val activity = this
    footer.setOnClickListener(new View.OnClickListener() {
      def onClick(v: View) {
        db.filterStories({
          val arr = ArrayBuilder.make[Long]
          cursor.moveToFirst()
          while (!cursor.isAfterLast) {
            arr += cursor.getLong(0)
            cursor.moveToNext()
          }
          arr.result()
        }, false)
        updateView()
        activity.getListView.setSelectionAfterHeaderView()
      }
    })

    registerForContextMenu(getListView)    
    cursor = db.storyView()
    adapter = new SimpleCursorAdapter(
      this, R.layout.headline, cursor, Array("title", "link", "pos", "neg"),
      Array(R.id.headline_title, R.id.headline_link, R.id.headline_random, R.id.headline_weight))
    setListAdapter(adapter)

    PreferenceManager.setDefaultValues(this, R.xml.settings, false)
    PreferenceManager.getDefaultSharedPreferences(this)
      .registerOnSharedPreferenceChangeListener(this)
  }

  override def onCreateOptionsMenu(menu: Menu): Boolean = {
    val inflater = getMenuInflater()
    inflater.inflate(R.menu.headlines, menu)
    true
  }

  override def onOptionsItemSelected(item: MenuItem): Boolean = 
    item.getItemId() match {
      case R.id.refresh => {
        db.purgeOld()
        for (link <- db.refreshLinks()) {
          refreshFeed(link._1, link._2, link._3, link._4)
        }
        updateView()
        true
      }
      case R.id.feeds => {
        startActivity(new Intent(this, classOf[NWSRFeeds]))
        true
      }
      case R.id.saved => {
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

  def onSharedPreferenceChanged(sp: SharedPreferences, key: String) {
    if (key == "stories_per_page") {
      cursor = db.storyView()
      adapter.changeCursor(cursor)
    }
  }
}
