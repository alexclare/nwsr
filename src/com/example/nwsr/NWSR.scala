package com.example.nwsr

import android.app.Dialog
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.AsyncTask
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
  activity =>
  val NextPage: Int = 2

  val errorDialogs = false

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    setTitle(R.string.title_headlines)
    setContentView(R.layout.headlines)

    val inflater = LayoutInflater.from(this)
    val footer = inflater.inflate(R.layout.button_next_headline, null)
      .asInstanceOf[TextView]
    getListView.addFooterView(footer)
    footer.setOnClickListener(new View.OnClickListener() {
      def onClick(v: View) {
        val ids = {
          val arr = ArrayBuilder.make[Long]
          cursor.moveToFirst()
          while (!cursor.isAfterLast) {
            arr += cursor.getLong(0)
            cursor.moveToNext()
          }
          arr.result()
        }
        new AsyncTask[Object, Unit, Unit]() {
          override def onPreExecute() = {
            showDialog(NextPage)
          }

          def doInBackground(a: Object*) {
            db.filterStories(ids, false)
          }

          override def onPostExecute(a: Unit) {
            updateView()
            activity.getListView.setSelectionAfterHeaderView()
            dismissDialog(NextPage)
          }
        }.execute()
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
        db.purgeOld()
        for (link <- db.refreshLinks()) {
          refreshFeed(Some(link._1), link._2, link._3, link._4)
        }
        updateView()
        true
      }
      case R.id.feeds => {
        startActivity(new Intent(this, classOf[NWSRFeeds]))
        true
      }
      case R.id.saved => {
        startActivity(new Intent(this, classOf[NWSRSaved]))
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
    val info = item.getMenuInfo.asInstanceOf[
      AdapterView.AdapterContextMenuInfo]
    item.getItemId match {
      case R.id.open_browser => {
        val url = info.targetView.findViewById(R.id.headline_link)
          .asInstanceOf[TextView].getText.toString
        db.filterStories(Array(info.id), true)
        openInBrowser(url)
        // This updateView is probably unnecessary (it's called in onResume,
        //   and we come back from an activity), but just in case
        updateView()
        true
      }
      case R.id.save => {
        db.addSaved(Story(
          info.targetView.findViewById(R.id.headline_title)
            .asInstanceOf[TextView].getText.toString,
          info.targetView.findViewById(R.id.headline_link)
            .asInstanceOf[TextView].getText.toString))
        db.filterStories(Array(info.id), true)
        updateView()
        true
      }
      case R.id.delete => {
        db.filterStories(Array(info.id), false)
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

  override def onCreateDialog(id: Int): Dialog = id match {
    case NextPage => ProgressDialog.show(this, "", "Working...", true)
    case _ => super.onCreateDialog(id)
  }
}
