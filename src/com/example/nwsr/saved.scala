package com.example.nwsr

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ContextMenu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.SimpleCursorAdapter
import android.widget.TextView


class NWSRSaved extends DatabaseActivity {
  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.saved)
    
    registerForContextMenu(getListView)
    cursor = db.savedView()
    adapter = new SimpleCursorAdapter(
      this, R.layout.headline, cursor, Array("title", "link", "_id", "_id"),
      Array(R.id.headline_title, R.id.headline_link, R.id.headline_random, R.id.headline_weight))
    setListAdapter(adapter)
  }

  override def onCreateContextMenu(menu: ContextMenu, v: View,
                                   menuInfo: ContextMenu.ContextMenuInfo) {
    super.onCreateContextMenu(menu, v, menuInfo)
    if (menuInfo.asInstanceOf[AdapterView.AdapterContextMenuInfo].id >= 0) {
      val inflater = getMenuInflater()
      inflater.inflate(R.menu.context_saved, menu)
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
        startActivity(new Intent(Intent.ACTION_VIEW)
                      .setData(Uri.parse(if (url.startsWith("http://")) url
                                         else "http://" + url)))
        true
      }
      case R.id.delete => {
        db.deleteSaved(info.id)
        updateView()
        true
      }
      case _ => super.onContextItemSelected(item)
    }
  }
}

