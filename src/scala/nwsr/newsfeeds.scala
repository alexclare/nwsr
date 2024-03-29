package com.aquamentis.nwsr

import android.app.AlertDialog
import android.app.Dialog
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.view.ContextMenu
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ListView
import android.widget.SimpleCursorAdapter
import android.widget.EditText
import android.widget.TextView

import DialogType._

class NewsFeeds extends DatabaseActivity with DialogFeedRetriever {

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.feeds)

    val inflater = LayoutInflater.from(this)
    getListView.addHeaderView(inflater.inflate(R.layout.button_add_feed, null))

    registerForContextMenu(getListView)
    getListView.setLongClickable(false)
    cursor = db.feedView()
    adapter = new SimpleCursorAdapter(
      this, R.layout.feed, cursor, Array("title", "display_link"),
      Array(R.id.feed_title, R.id.feed_link))
    setListAdapter(adapter)
  }

  override def onResume() {
    super.onResume()
    if (getIntent.getAction == Intent.ACTION_VIEW) {
      // Issue 950 causes some feeds not to be recognized by the intent
      //   filter; fixed in 2.2
      new DialogRetrieveTask().execute(FeedLink(getIntent.getDataString))
    }
    updateView()
  }

  override def onListItemClick(lv: ListView, v: View, pos: Int, id: Long) {
    if (id < 0) {
      showDialog(AddFeed)
    } else {
      lv.showContextMenuForChild(v)
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
        new DialogRetrieveTask().execute(FeedId(info.id))
        true
      }
      case R.id.open_browser => {
        openInBrowser(info.targetView.findViewById(R.id.feed_link)
          .asInstanceOf[TextView].getText.toString)
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

  override def onCreateDialog(id: Int): Dialog = DialogType(id) match {
    case AddFeed => {
      val builder = new AlertDialog.Builder(this)
      val inflater = LayoutInflater.from(this)
      val layout = inflater.inflate(R.layout.add_feed, null)
      builder.setPositiveButton(
        R.string.dialog_ok,
        new DialogInterface.OnClickListener () {
          def onClick(dialog: DialogInterface, button: Int) {
            new DialogRetrieveTask().execute(
              FeedLink(layout.findViewById(R.id.add_feed_url)
                       .asInstanceOf[EditText].getText().toString()))
            dialog.dismiss()
          }
        })
      builder.setNegativeButton(
        R.string.dialog_cancel,
        new DialogInterface.OnClickListener () {
          def onClick(dialog: DialogInterface, button: Int) {
            dialog.dismiss()
          }
        })
      builder.setView(layout)
      builder.setTitle(R.string.feeds_add_text)
      builder.create()
    }
    case _ => super.onCreateDialog(id)
  }

  override def onPrepareDialog(id: Int, dialog: Dialog) = DialogType(id) match {
    case AddFeed => {
      dialog.findViewById(R.id.add_feed_url)
        .asInstanceOf[EditText].setText("")
    }
    case _ => super.onPrepareDialog(id, dialog)
  }
}
