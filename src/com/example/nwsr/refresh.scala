package com.example.nwsr

import android.app.AlarmManager
import android.app.IntentService
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.preference.PreferenceManager

import java.io.FileNotFoundException
import java.io.IOException
import java.net.UnknownHostException

import org.xml.sax.SAXParseException

import com.example.util.Feed
import com.example.util.NotFeedException

class RefreshService extends IntentService ("NWSRRefreshService") {
  var db: NWSRDatabase = _

  override def onCreate() {
    super.onCreate()
    db = new NWSRDatabase(this).open()
  }

  override def onDestroy() {
    super.onDestroy()
    db.close()
  }

  override def onHandleIntent(intent: Intent) {
    if (getSystemService(Context.CONNECTIVITY_SERVICE)
        .asInstanceOf[ConnectivityManager].getBackgroundDataSetting) {
      val cursor = db.feedsToRefresh(None)
      cursor.moveToFirst()
      while(!cursor.isAfterLast) {
        try {
          Feed.refresh(
            cursor.getString(1),
            cursor.getString(2) match {
              case null => None
              case e => Some(e)
            },
            cursor.getString(3) match {
              case null => None
              case l => Some(l)
            }) match {
            case Some(f) => db.addFeed(f, Some(cursor.getLong(0)))
            case None =>
          }
        } catch {
          case _ @ (_: FileNotFoundException | _: UnknownHostException |
                    _: IOException) => 
          case _ @ (_: SAXParseException | _: NotFeedException) => 
        }
        cursor.moveToNext()
      }
      cursor.close()
    }
  }
}

class BootReceiver extends BroadcastReceiver {
  override def onReceive(context: Context, intent: Intent) {
    val manager = context.getSystemService(Context.ALARM_SERVICE)
      .asInstanceOf[AlarmManager]
    val service = PendingIntent.getService(
      context, 0, new Intent(context, classOf[RefreshService]),
      PendingIntent.FLAG_UPDATE_CURRENT)
    val interval = System.currentTimeMillis + 3600000
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    prefs.getString("feed_refresh_rate", "0") match {
      case "1" => manager.setInexactRepeating(
        AlarmManager.RTC_WAKEUP, interval,
        AlarmManager.INTERVAL_HOUR, service)
      case "2" => manager.setInexactRepeating(
        AlarmManager.RTC_WAKEUP, interval,
        AlarmManager.INTERVAL_HALF_DAY, service)
      case "3" => manager.setInexactRepeating(
        AlarmManager.RTC_WAKEUP, interval,
        AlarmManager.INTERVAL_DAY, service)
      case _ =>
    }
  }
}
